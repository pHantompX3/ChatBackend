#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { run as runDiscovery } from "./discover-postman.mjs";

const __dirname = path.dirname(new URL(import.meta.url).pathname);
const repoRoot = path.resolve(__dirname, "..", "..");

const defaultConfigPath = path.join(repoRoot, "postman", "config.properties");
const defaultCollectionPaths = [
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-user-flows.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-websocket-manual-integration.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-websocket-participants-down.postman_collection.json",
  ),
];
const defaultEnvironmentPaths = [
  path.join(
    repoRoot,
    "postman",
    "environments",
    "local.example.postman_environment.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "environments",
    "devdocker.example.postman_environment.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "environments",
    "harddocker.example.postman_environment.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "environments",
    "production.example.postman_environment.json",
  ),
];

const args = new Set(process.argv.slice(2));
const dryRun = args.has("--dry-run");
const createMissing = args.has("--create-missing");
const skipEnvironment = args.has("--skip-environment");
const checkDrift = args.has("--check-drift");

if (!dryRun && !checkDrift) {
  runDiscovery();
}

function log(msg) {
  console.log(msg);
}

function fail(msg) {
  console.error(`ERROR: ${msg}`);
  process.exit(1);
}

function parseProperties(content) {
  const result = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const eq = trimmed.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    result[key] = value;
  }
  return result;
}

function loadConfig(configPath) {
  const props = fs.existsSync(configPath)
    ? parseProperties(fs.readFileSync(configPath, "utf8"))
    : {};

  const get = (envKey, propKeys = []) => {
    if (process.env[envKey]) {
      return process.env[envKey].trim();
    }
    for (const propKey of propKeys) {
      if (props[propKey]) {
        return props[propKey].trim();
      }
    }
    return "";
  };

  return {
    configPath,
    apiKey: get("POSTMAN_API_KEY", ["postman-api-key", "POSTMAN_API_KEY"]),
    workspaceId: get("POSTMAN_WORKSPACE_ID", [
      "postman-workspace-id",
      "POSTMAN_WORKSPACE_ID",
    ]),
    collectionId: get("POSTMAN_COLLECTION_ID", [
      "postman-collection-id",
      "POSTMAN_COLLECTION_ID",
    ]),
    flowCollectionId: get("POSTMAN_FLOW_COLLECTION_ID", [
      "postman-flow-collection-id",
      "POSTMAN_FLOW_COLLECTION_ID",
    ]),
    websocketCollectionId: get("POSTMAN_WEBSOCKET_COLLECTION_ID", [
      "postman-websocket-collection-id",
      "POSTMAN_WEBSOCKET_COLLECTION_ID",
    ]),
    websocketTeardownCollectionId: get(
      "POSTMAN_WEBSOCKET_TEARDOWN_COLLECTION_ID",
      [
        "postman-websocket-teardown-collection-id",
        "POSTMAN_WEBSOCKET_TEARDOWN_COLLECTION_ID",
      ],
    ),
    environmentId: get("POSTMAN_ENVIRONMENT_ID", [
      "postman-environment-id",
      "POSTMAN_ENVIRONMENT_ID",
    ]),
    localEnvironmentId: get("POSTMAN_LOCAL_ENVIRONMENT_ID", [
      "postman-local-environment-id",
      "POSTMAN_LOCAL_ENVIRONMENT_ID",
    ]),
    devEnvironmentId: get("POSTMAN_DEV_ENVIRONMENT_ID", [
      "postman-dev-environment-id",
      "POSTMAN_DEV_ENVIRONMENT_ID",
    ]),
    hardDockerEnvironmentId: get("POSTMAN_HARDDOCKER_ENVIRONMENT_ID", [
      "postman-harddocker-environment-id",
      "POSTMAN_HARDDOCKER_ENVIRONMENT_ID",
    ]),
    prodEnvironmentId: get("POSTMAN_PROD_ENVIRONMENT_ID", [
      "postman-prod-environment-id",
      "POSTMAN_PROD_ENVIRONMENT_ID",
    ]),
  };
}

function environmentIdKeyForFile(environmentPath) {
  const fileName = path.basename(environmentPath);
  if (fileName === "local.example.postman_environment.json") {
    return {
      id: "localEnvironmentId",
      property: "postman-local-environment-id",
      label: "Local",
    };
  }
  if (fileName === "devdocker.example.postman_environment.json") {
    return {
      id: "devEnvironmentId",
      property: "postman-dev-environment-id",
      label: "DevDocker",
    };
  }
  if (fileName === "harddocker.example.postman_environment.json") {
    return {
      id: "hardDockerEnvironmentId",
      property: "postman-harddocker-environment-id",
      label: "HardDocker",
    };
  }
  if (fileName === "production.example.postman_environment.json") {
    return {
      id: "prodEnvironmentId",
      property: "postman-prod-environment-id",
      label: "Production",
    };
  }

  return {
    id: "environmentId",
    property: "postman-environment-id",
    label: fileName,
  };
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`Unable to parse JSON file ${filePath}`);
  }
}

function stableStringify(value) {
  if (Array.isArray(value)) {
    return `[${value.map((entry) => stableStringify(entry)).join(",")}]`;
  }
  if (value && typeof value === "object") {
    const keys = Object.keys(value)
      .filter((key) => value[key] !== undefined)
      .sort((a, b) => a.localeCompare(b));
    return `{${keys
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

function firstDifferencePath(localValue, remoteValue, path = "collection") {
  if (stableStringify(localValue) === stableStringify(remoteValue)) {
    return null;
  }
  if (Array.isArray(localValue) && Array.isArray(remoteValue)) {
    const length = Math.max(localValue.length, remoteValue.length);
    for (let index = 0; index < length; index += 1) {
      const difference = firstDifferencePath(
        localValue[index],
        remoteValue[index],
        `${path}[${index}]`,
      );
      if (difference) {
        return difference;
      }
    }
  }
  if (
    localValue &&
    remoteValue &&
    typeof localValue === "object" &&
    typeof remoteValue === "object" &&
    !Array.isArray(localValue) &&
    !Array.isArray(remoteValue)
  ) {
    const keys = [...new Set([...Object.keys(localValue), ...Object.keys(remoteValue)])]
      .sort((a, b) => a.localeCompare(b));
    for (const key of keys) {
      const difference = firstDifferencePath(
        localValue[key],
        remoteValue[key],
        `${path}.${key}`,
      );
      if (difference) {
        return difference;
      }
    }
  }
  return path;
}

function normalizeCollection(collection) {
  const normalizeVariable = (variable) => ({
    key: variable?.key ?? "",
    value: variable?.value ?? "",
    type: variable?.type ?? undefined,
    description: variable?.description ?? undefined,
  });

  const normalizeHeader = (header) => ({
    key: header?.key ?? "",
    value: header?.value ?? "",
    disabled: header?.disabled ?? false,
  });

  const normalizeUrl = (url) => {
    if (!url || typeof url !== "object") {
      return url;
    }
    return {
      raw: url.raw ?? undefined,
    };
  };

  const normalizeBody = (body) => {
    if (!body || typeof body !== "object") {
      return body;
    }
    return {
      mode: body.mode ?? undefined,
      raw: body.raw ?? undefined,
      formdata: Array.isArray(body.formdata)
        ? body.formdata.map((entry) => ({
            key: entry?.key ?? "",
            value: entry?.value ?? "",
            type: entry?.type ?? undefined,
            src: entry?.src ?? undefined,
            disabled: entry?.disabled ?? false,
          }))
        : undefined,
      urlencoded: Array.isArray(body.urlencoded)
        ? body.urlencoded.map((entry) => ({
            key: entry?.key ?? "",
            value: entry?.value ?? "",
            type: entry?.type ?? undefined,
            disabled: entry?.disabled ?? false,
          }))
        : undefined,
    };
  };

  const normalizeRequest = (request) => {
    if (!request || typeof request !== "object") {
      return request;
    }
    return {
      method: request.method ?? undefined,
      header: Array.isArray(request.header) && request.header.length > 0
        ? request.header.map(normalizeHeader)
        : undefined,
      url: normalizeUrl(request.url),
      body: normalizeBody(request.body),
      description: request.description ?? undefined,
    };
  };

  const normalizeResponse = (response) => ({
    name: response?.name ?? "",
    status: response?.status ?? undefined,
    code: response?.code ?? undefined,
    body: response?.body ?? undefined,
  });

  const normalizeEvent = (event) => ({
    listen: event?.listen ?? "",
    script: event?.script
      ? {
          type: event.script.type ?? undefined,
          exec: Array.isArray(event.script.exec)
            ? event.script.exec
            : undefined,
        }
      : undefined,
  });

  const normalizeItem = (item) => ({
    name: item?.name ?? "",
    description: item?.description ?? undefined,
    item: Array.isArray(item?.item) ? item.item.map(normalizeItem) : undefined,
    request: normalizeRequest(item?.request),
    response: Array.isArray(item?.response) && item.response.length > 0
      ? item.response.map(normalizeResponse)
      : undefined,
    event: Array.isArray(item?.event)
      ? item.event.map(normalizeEvent)
      : undefined,
    variable: Array.isArray(item?.variable)
      ? item.variable.map(normalizeVariable)
      : undefined,
    protocolProfileBehavior: item?.protocolProfileBehavior ?? undefined,
  });

  return {
    info: {
      name: collection?.info?.name ?? "",
      description: collection?.info?.description ?? undefined,
      schema: collection?.info?.schema ?? undefined,
    },
    item: Array.isArray(collection?.item)
      ? collection.item.map(normalizeItem)
      : [],
    event: Array.isArray(collection?.event)
      ? collection.event.map(normalizeEvent)
      : undefined,
    variable: Array.isArray(collection?.variable)
      ? collection.variable
          .map(normalizeVariable)
          .sort((a, b) => a.key.localeCompare(b.key))
      : undefined,
  };
}

function normalizeEnvironment(environment) {
  const values = Array.isArray(environment?.values)
    ? environment.values
        .map((entry) => ({
          key: entry?.key ?? "",
          value: entry?.value ?? "",
          type: entry?.type ?? "default",
          enabled: entry?.enabled ?? true,
        }))
        .sort((a, b) => a.key.localeCompare(b.key))
    : [];

  return {
    name: environment?.name ?? "",
    values,
  };
}

async function runDriftCheck(config, localCollection, collectionId) {
  if (!collectionId) {
    fail(
      "Collection id is required for --check-drift. Set postman-collection-id (or postman-flow-collection-id for flow collection) in local ignored config.",
    );
  }

  const remoteCollectionEnvelope = await postmanRequest(
    config,
    "GET",
    `/collections/${encodeURIComponent(collectionId)}`,
  );
  const remoteCollection = remoteCollectionEnvelope?.collection;
  if (!remoteCollection) {
    fail("Configured collection id could not be fetched for drift checking.");
  }

  const normalizedLocalCollection = normalizeCollection(localCollection);
  const normalizedRemoteCollection = normalizeCollection(remoteCollection);
  const localCollectionFingerprint = stableStringify(normalizedLocalCollection);
  const remoteCollectionFingerprint = stableStringify(normalizedRemoteCollection);

  let hasDrift = false;
  if (localCollectionFingerprint !== remoteCollectionFingerprint) {
    hasDrift = true;
    const differencePath = firstDifferencePath(
      normalizedLocalCollection,
      normalizedRemoteCollection,
    );
    log(
      `Drift detected: collection artifact differs from Postman Cloud target${differencePath ? ` at ${differencePath}` : ""}.`,
    );
  }

  if (hasDrift) {
    fail(
      "Postman drift check failed. Run sync-postman.sh to reconcile cloud resources with repository artifacts.",
    );
  }

  log(
    "No Postman drift detected between repository artifacts and configured cloud targets.",
  );
}

function ensureRequired(config) {
  if (!config.apiKey) {
    fail(
      "Missing Postman API key. Set POSTMAN_API_KEY or postman-api-key in local ignored config.",
    );
  }
  if (!config.workspaceId) {
    fail(
      "Missing workspace id. Set POSTMAN_WORKSPACE_ID or postman-workspace-id in local ignored config.",
    );
  }
}

function writeProperty(filePath, key, value) {
  const lines = fs.existsSync(filePath)
    ? fs.readFileSync(filePath, "utf8").split(/\r?\n/)
    : [];
  let replaced = false;
  const updated = lines.map((line) => {
    const trimmed = line.trim();
    if (!trimmed.startsWith("#") && trimmed.startsWith(`${key}=`)) {
      replaced = true;
      return `${key}=${value}`;
    }
    return line;
  });
  if (!replaced) {
    updated.push(`${key}=${value}`);
  }
  fs.writeFileSync(
    filePath,
    `${updated.join("\n").replace(/\n+$/, "")}\n`,
    "utf8",
  );
}

async function postmanRequest(config, method, apiPath, body = undefined) {
  const endpoint = `https://api.getpostman.com${apiPath}`;
  const response = await fetch(endpoint, {
    method,
    headers: {
      "X-Api-Key": config.apiKey,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    if (response.status === 401) {
      fail(
        "Postman API authentication failed (401). Check API key configuration.",
      );
    }
    if (response.status === 403) {
      fail(
        "Postman API authorization failed (403). Ensure key has workspace access.",
      );
    }
    if (response.status === 404) {
      fail(`Postman resource not found for ${method} ${apiPath}.`);
    }
    if (response.status === 429) {
      fail("Postman API rate limit reached (429). Retry later.");
    }
    fail(
      `Postman API request failed (${response.status}) for ${method} ${apiPath}.`,
    );
  }

  return response.json();
}

function workspaceHasCollection(workspace, collectionId) {
  if (!collectionId) {
    return true;
  }
  return (workspace?.collections ?? []).some(
    (c) =>
      c.id === collectionId ||
      c.uid === collectionId ||
      c.name === collectionId,
  );
}

function workspaceHasEnvironment(workspace, environmentId) {
  if (!environmentId) {
    return true;
  }
  return (workspace?.environments ?? []).some(
    (e) =>
      e.id === environmentId ||
      e.uid === environmentId ||
      e.name === environmentId,
  );
}

function findWorkspaceEnvironmentIdByName(workspace, environmentName) {
  if (!environmentName) {
    return "";
  }
  const match = (workspace?.environments ?? []).find(
    (e) => e.name === environmentName,
  );
  return match?.uid || match?.id || "";
}

async function syncCollection(config, workspaceId, collection, collectionId) {
  const targetPropertyKey = "postman-collection-id";
  const targetLabel = "Primary";
  if (!collectionId) {
    if (!createMissing) {
      fail(
        "Collection id is missing. Set postman-collection-id or rerun with --create-missing.",
      );
    }

    if (dryRun) {
      log("Dry-run: would create collection in configured workspace.");
      return "";
    }

    const created = await postmanRequest(
      config,
      "POST",
      `/collections?workspace=${encodeURIComponent(workspaceId)}`,
      { collection },
    );

    const newId = created?.collection?.uid || created?.collection?.id;
    if (!newId) {
      fail("Postman collection creation returned no collection identifier.");
    }

    writeProperty(config.configPath, targetPropertyKey, newId);
    log(
      `${targetLabel} collection created and local config updated with ${targetPropertyKey}.`,
    );
    return newId;
  }

  if (dryRun) {
    log("Dry-run: would update configured collection in Postman Cloud.");
    return collectionId;
  }

  await postmanRequest(
    config,
    "PUT",
    `/collections/${encodeURIComponent(collectionId)}`,
    {
      collection,
    },
  );
  log("Collection synchronized successfully.");
  return collectionId;
}

async function syncCollectionTarget(config, workspaceId, collection, target) {
  if (!target.collectionId) {
    if (!createMissing) {
      log(
        `${target.label} collection id not configured; skipping collection sync.`,
      );
      return "";
    }

    if (dryRun) {
      log(
        `Dry-run: would create ${target.label} collection in configured workspace.`,
      );
      return "";
    }

    const created = await postmanRequest(
      config,
      "POST",
      `/collections?workspace=${encodeURIComponent(workspaceId)}`,
      { collection },
    );

    const newId = created?.collection?.uid || created?.collection?.id;
    if (!newId) {
      fail("Postman collection creation returned no collection identifier.");
    }

    writeProperty(config.configPath, target.propertyKey, newId);
    log(
      `${target.label} collection created and local config updated with ${target.propertyKey}.`,
    );
    target.collectionId = newId;
    return newId;
  }

  if (dryRun) {
    log(`Dry-run: would update ${target.label} collection in Postman Cloud.`);
    return target.collectionId;
  }

  await postmanRequest(
    config,
    "PUT",
    `/collections/${encodeURIComponent(target.collectionId)}`,
    {
      collection,
    },
  );
  log(`${target.label} collection synchronized successfully.`);
  return target.collectionId;
}

async function syncEnvironment(config, workspaceId, environment, syncTarget) {
  if (skipEnvironment) {
    log("Environment sync skipped by --skip-environment.");
    return "";
  }

  const environmentId = syncTarget.environmentId;
  const environmentLabel = syncTarget.label;

  if (!environmentId) {
    if (!createMissing) {
      log(
        `Environment id not configured for ${environmentLabel}; skipping environment sync.`,
      );
      return "";
    }

    if (dryRun) {
      log(
        `Dry-run: would create ${environmentLabel} environment in configured workspace.`,
      );
      return "";
    }

    const created = await postmanRequest(
      config,
      "POST",
      `/environments?workspace=${encodeURIComponent(workspaceId)}`,
      { environment },
    );

    const newId = created?.environment?.uid || created?.environment?.id;
    if (!newId) {
      fail("Postman environment creation returned no environment identifier.");
    }

    writeProperty(config.configPath, syncTarget.propertyKey, newId);
    log(
      `${environmentLabel} environment created and local config updated with ${syncTarget.propertyKey}.`,
    );
    return newId;
  }

  if (dryRun) {
    log(
      `Dry-run: would update ${environmentLabel} environment in Postman Cloud.`,
    );
    return environmentId;
  }

  await postmanRequest(
    config,
    "PUT",
    `/environments/${encodeURIComponent(environmentId)}`,
    {
      environment,
    },
  );
  log(`${environmentLabel} environment synchronized successfully.`);
  return environmentId;
}

async function run() {
  const configPath = process.env.POSTMAN_CONFIG_FILE
    ? path.resolve(process.env.POSTMAN_CONFIG_FILE)
    : defaultConfigPath;
  const config = loadConfig(configPath);

  if (checkDrift && createMissing) {
    fail("--check-drift cannot be combined with --create-missing.");
  }
  if (checkDrift && dryRun) {
    fail("--check-drift cannot be combined with --dry-run.");
  }

  ensureRequired(config);

  const collections = defaultCollectionPaths.map((collectionPath) => ({
    path: collectionPath,
    content: readJson(collectionPath),
  }));
  const collectionTargets = collections.map(({ path: collectionPath }) => {
    const collectionName = path.basename(collectionPath);
    if (collectionName === "chat-backend-user-flows.postman_collection.json") {
      return {
        collectionPath,
        label: "Flow",
        propertyKey: "postman-flow-collection-id",
        collectionId: config.flowCollectionId,
      };
    }
    if (
      collectionName ===
      "chat-backend-websocket-manual-integration.postman_collection.json"
    ) {
      return {
        collectionPath,
        label: "WebSocket manual integration",
        propertyKey: "postman-websocket-collection-id",
        collectionId: config.websocketCollectionId,
      };
    }
    if (
      collectionName ===
      "chat-backend-websocket-participants-down.postman_collection.json"
    ) {
      return {
        collectionPath,
        label: "WebSocket participants bring-down",
        propertyKey: "postman-websocket-teardown-collection-id",
        collectionId: config.websocketTeardownCollectionId,
      };
    }

    return {
      collectionPath,
      label: "Primary",
      propertyKey: "postman-collection-id",
      collectionId: config.collectionId,
    };
  });
  const environments = defaultEnvironmentPaths.map((environmentPath) => {
    const descriptor = environmentIdKeyForFile(environmentPath);
    const configuredEnvironmentId =
      descriptor.id === "localEnvironmentId"
        ? config.localEnvironmentId || config.environmentId
        : config[descriptor.id];
    const content = readJson(environmentPath);

    return {
      path: environmentPath,
      content,
      label: descriptor.label,
      propertyKey: descriptor.property,
      environmentId: configuredEnvironmentId,
    };
  });

  const workspaceEnvelope = await postmanRequest(
    config,
    "GET",
    `/workspaces/${encodeURIComponent(config.workspaceId)}`,
  );
  const workspace = workspaceEnvelope?.workspace;
  if (!workspace?.id) {
    fail("Configured workspace could not be resolved.");
  }

  for (const environmentTarget of environments) {
    if (!environmentTarget.environmentId) {
      environmentTarget.environmentId = findWorkspaceEnvironmentIdByName(
        workspace,
        environmentTarget.content?.name,
      );
    }
  }

  const collectionIdsToCheck = collectionTargets
    .map((target) => target.collectionId)
    .filter(Boolean)
    .filter((value, index, arr) => arr.indexOf(value) === index);

  for (const collectionId of collectionIdsToCheck) {
    if (!workspaceHasCollection(workspace, collectionId)) {
      fail(
        "Configured collection id is not present in the configured workspace. Refusing overwrite.",
      );
    }
  }

  for (const environmentTarget of environments) {
    if (
      environmentTarget.environmentId &&
      !workspaceHasEnvironment(workspace, environmentTarget.environmentId)
    ) {
      fail(
        `Configured environment id for ${environmentTarget.label} is not present in the configured workspace. Refusing overwrite.`,
      );
    }
  }

  for (const target of collectionTargets) {
    if (!target.collectionId) {
      continue;
    }
    await postmanRequest(
      config,
      "GET",
      `/collections/${encodeURIComponent(target.collectionId)}`,
    );
  }

  if (checkDrift) {
    for (const { path: collectionPath, content: collection } of collections) {
      const target = collectionTargets.find(
        (entry) => entry.collectionPath === collectionPath,
      );
      const targetCollectionId = target?.collectionId;

      if (!targetCollectionId) {
        fail(
          `Collection id is required for --check-drift (${target?.label || path.basename(collectionPath)}). Set ${target?.propertyKey || "postman-collection-id"} in local ignored config.`,
        );
      }

      await runDriftCheck(config, collection, targetCollectionId);
      log(
        `Collection drift check completed for ${path.relative(repoRoot, collectionPath)}.`,
      );
    }

    if (!skipEnvironment) {
      for (const environmentTarget of environments) {
        if (!environmentTarget.environmentId) {
          fail(
            `Environment id is required for --check-drift (${environmentTarget.label}). Set ${environmentTarget.propertyKey} in local ignored config or use --skip-environment.`,
          );
        }

        const remoteEnvironmentEnvelope = await postmanRequest(
          config,
          "GET",
          `/environments/${encodeURIComponent(environmentTarget.environmentId)}`,
        );
        const remoteEnvironment = remoteEnvironmentEnvelope?.environment;
        if (!remoteEnvironment) {
          fail(
            `Configured environment id for ${environmentTarget.label} could not be fetched for drift checking.`,
          );
        }

        const normalizedLocalEnvironment = normalizeEnvironment(
          environmentTarget.content,
        );
        const normalizedRemoteEnvironment =
          normalizeEnvironment(remoteEnvironment);
        const localEnvironmentFingerprint = stableStringify(
          normalizedLocalEnvironment,
        );
        const remoteEnvironmentFingerprint = stableStringify(
          normalizedRemoteEnvironment,
        );

        if (localEnvironmentFingerprint !== remoteEnvironmentFingerprint) {
          const differencePath = firstDifferencePath(
            normalizedLocalEnvironment,
            normalizedRemoteEnvironment,
            "environment",
          );
          fail(
            `Drift detected: ${environmentTarget.label} environment artifact differs from Postman Cloud target${
              differencePath ? ` at ${differencePath}` : ""
            }.`,
          );
        }

        log(
          `Environment drift check completed for ${path.relative(repoRoot, environmentTarget.path)}.`,
        );
      }
    }

    log(
      "No Postman drift detected between repository artifacts and configured cloud targets.",
    );
    return;
  }

  for (const { path: collectionPath, content: collection } of collections) {
    const target = collectionTargets.find(
      (entry) => entry.collectionPath === collectionPath,
    );

    const syncedCollectionId = await syncCollectionTarget(
      config,
      config.workspaceId,
      collection,
      target,
    );
    if (syncedCollectionId) {
      log(
        `Collection target confirmed for ${path.relative(repoRoot, collectionPath)}.`,
      );
    }
  }

  const syncedEnvironmentIds = [];
  for (const environmentTarget of environments) {
    const syncedEnvironmentId = await syncEnvironment(
      config,
      config.workspaceId,
      environmentTarget.content,
      environmentTarget,
    );
    if (syncedEnvironmentId) {
      syncedEnvironmentIds.push({
        label: environmentTarget.label,
        id: syncedEnvironmentId,
      });
    }
  }

  log(`Sync complete for workspace ${config.workspaceId}.`);
  if (syncedEnvironmentIds.length > 0) {
    for (const synced of syncedEnvironmentIds) {
      log(`${synced.label} environment target confirmed.`);
    }
  }
}

run().catch((error) => {
  fail(error instanceof Error ? error.message : String(error));
});
