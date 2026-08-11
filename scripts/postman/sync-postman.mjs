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
];
const defaultEnvironmentPath = path.join(
  repoRoot,
  "postman",
  "environments",
  "local.example.postman_environment.json",
);

runDiscovery();

const args = new Set(process.argv.slice(2));
const dryRun = args.has("--dry-run");
const createMissing = args.has("--create-missing");
const skipEnvironment = args.has("--skip-environment");
const checkDrift = args.has("--check-drift");

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
    environmentId: get("POSTMAN_ENVIRONMENT_ID", [
      "postman-environment-id",
      "POSTMAN_ENVIRONMENT_ID",
    ]),
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
      header: Array.isArray(request.header)
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
    response: Array.isArray(item?.response)
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
          type: entry?.type ?? undefined,
          enabled: entry?.enabled ?? true,
        }))
        .sort((a, b) => a.key.localeCompare(b.key))
    : [];

  return {
    name: environment?.name ?? "",
    values,
  };
}

async function runDriftCheck(config, localCollection, localEnvironment) {
  if (!config.collectionId) {
    fail(
      "Collection id is required for --check-drift. Set postman-collection-id in local ignored config.",
    );
  }

  const remoteCollectionEnvelope = await postmanRequest(
    config,
    "GET",
    `/collections/${encodeURIComponent(config.collectionId)}`,
  );
  const remoteCollection = remoteCollectionEnvelope?.collection;
  if (!remoteCollection) {
    fail("Configured collection id could not be fetched for drift checking.");
  }

  const localCollectionFingerprint = stableStringify(
    normalizeCollection(localCollection),
  );
  const remoteCollectionFingerprint = stableStringify(
    normalizeCollection(remoteCollection),
  );

  let hasDrift = false;
  if (localCollectionFingerprint !== remoteCollectionFingerprint) {
    hasDrift = true;
    log(
      "Drift detected: collection artifact differs from Postman Cloud target.",
    );
  }

  if (!skipEnvironment) {
    if (!config.environmentId) {
      fail(
        "Environment id is required for --check-drift unless --skip-environment is set.",
      );
    }

    const remoteEnvironmentEnvelope = await postmanRequest(
      config,
      "GET",
      `/environments/${encodeURIComponent(config.environmentId)}`,
    );
    const remoteEnvironment = remoteEnvironmentEnvelope?.environment;
    if (!remoteEnvironment) {
      fail(
        "Configured environment id could not be fetched for drift checking.",
      );
    }

    const localEnvironmentFingerprint = stableStringify(
      normalizeEnvironment(localEnvironment),
    );
    const remoteEnvironmentFingerprint = stableStringify(
      normalizeEnvironment(remoteEnvironment),
    );

    if (localEnvironmentFingerprint !== remoteEnvironmentFingerprint) {
      hasDrift = true;
      log(
        "Drift detected: environment artifact differs from Postman Cloud target.",
      );
    }
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

function containsWorkspaceResource(workspace, collectionId, environmentId) {
  const collectionMatch =
    !collectionId ||
    (workspace?.collections ?? []).some(
      (c) =>
        c.id === collectionId ||
        c.uid === collectionId ||
        c.name === collectionId,
    );

  const environmentMatch =
    !environmentId ||
    (workspace?.environments ?? []).some(
      (e) =>
        e.id === environmentId ||
        e.uid === environmentId ||
        e.name === environmentId,
    );

  return { collectionMatch, environmentMatch };
}

async function syncCollection(config, workspaceId, collection, collectionId) {
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

    writeProperty(config.configPath, "postman-collection-id", newId);
    log(
      "Collection created and local config updated with postman-collection-id.",
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

async function syncEnvironment(
  config,
  workspaceId,
  environment,
  environmentId,
) {
  if (skipEnvironment) {
    log("Environment sync skipped by --skip-environment.");
    return "";
  }

  if (!environmentId) {
    if (!createMissing) {
      log("Environment id not configured; skipping environment sync.");
      return "";
    }

    if (dryRun) {
      log("Dry-run: would create environment in configured workspace.");
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

    writeProperty(config.configPath, "postman-environment-id", newId);
    log(
      "Environment created and local config updated with postman-environment-id.",
    );
    return newId;
  }

  if (dryRun) {
    log("Dry-run: would update configured environment in Postman Cloud.");
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
  log("Environment synchronized successfully.");
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
  const environment = readJson(defaultEnvironmentPath);

  const workspaceEnvelope = await postmanRequest(
    config,
    "GET",
    `/workspaces/${encodeURIComponent(config.workspaceId)}`,
  );
  const workspace = workspaceEnvelope?.workspace;
  if (!workspace?.id) {
    fail("Configured workspace could not be resolved.");
  }

  const { collectionMatch, environmentMatch } = containsWorkspaceResource(
    workspace,
    config.collectionId,
    config.environmentId,
  );

  if (config.collectionId && !collectionMatch) {
    fail(
      "Configured collection id is not present in the configured workspace. Refusing overwrite.",
    );
  }
  if (config.environmentId && !environmentMatch) {
    fail(
      "Configured environment id is not present in the configured workspace. Refusing overwrite.",
    );
  }

  if (config.collectionId) {
    await postmanRequest(
      config,
      "GET",
      `/collections/${encodeURIComponent(config.collectionId)}`,
    );
  }
  if (config.environmentId && !skipEnvironment) {
    await postmanRequest(
      config,
      "GET",
      `/environments/${encodeURIComponent(config.environmentId)}`,
    );
  }

  if (checkDrift) {
    for (const { path: collectionPath, content: collection } of collections) {
      await runDriftCheck(config, collection, environment);
      log(
        `Drift check completed for ${path.relative(repoRoot, collectionPath)}.`,
      );
    }
    return;
  }

  for (const { path: collectionPath, content: collection } of collections) {
    const collectionName = path.basename(collectionPath);
    const targetCollectionId =
      collectionName === "chat-backend-user-flows.postman_collection.json"
        ? config.flowCollectionId || config.collectionId
        : config.collectionId;

    const syncedCollectionId = await syncCollection(
      config,
      config.workspaceId,
      collection,
      targetCollectionId,
    );
    if (syncedCollectionId) {
      log(
        `Collection target confirmed for ${path.relative(repoRoot, collectionPath)}.`,
      );
    }
  }

  const syncedEnvironmentId = await syncEnvironment(
    config,
    config.workspaceId,
    environment,
    config.environmentId,
  );

  log(`Sync complete for workspace ${config.workspaceId}.`);
  if (syncedEnvironmentId) {
    log("Environment target confirmed.");
  }
}

run().catch((error) => {
  fail(error instanceof Error ? error.message : String(error));
});
