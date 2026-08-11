#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const __dirname = path.dirname(new URL(import.meta.url).pathname);
const repoRoot = path.resolve(__dirname, "..", "..");

const javaRoot = path.join(repoRoot, "src", "main", "java");
const apiRoutesPath = path.join(
  repoRoot,
  "src",
  "main",
  "java",
  "com",
  "wayden",
  "messenger",
  "common",
  "api",
  "ApiRoutes.java",
);
const collectionPath = path.join(
  repoRoot,
  "postman",
  "collections",
  "chat-backend.postman_collection.json",
);

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    fail(`Unable to parse JSON file ${filePath}`);
  }
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function listJavaFiles(dirPath, out = []) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const entryPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      listJavaFiles(entryPath, out);
      continue;
    }
    if (entry.isFile() && entry.name.endsWith(".java")) {
      out.push(entryPath);
    }
  }
  return out;
}

function parseApiV1Constant() {
  const fallback = "/api/v1";
  if (!fs.existsSync(apiRoutesPath)) {
    return fallback;
  }
  const content = fs.readFileSync(apiRoutesPath, "utf8");
  const match = content.match(/API_V1\s*=\s*"([^"]+)"/);
  return match ? match[1] : fallback;
}

function decodePathExpression(expr, apiV1) {
  if (!expr) {
    return "";
  }
  const compact = expr.replace(/\s+/g, " ").trim();

  if (/^"[^"]*"$/.test(compact)) {
    return compact.slice(1, -1);
  }
  if (compact === "ApiRoutes.API_V1") {
    return apiV1;
  }
  const concatMatch = compact.match(/^ApiRoutes\.API_V1\s*\+\s*"([^"]*)"$/);
  if (concatMatch) {
    return `${apiV1}${concatMatch[1]}`;
  }

  return "";
}

function normalizePath(pathValue) {
  const trimmed = String(pathValue || "").trim();
  if (!trimmed) {
    return "";
  }
  const withLeadingSlash = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
  return withLeadingSlash.replace(/\/+/g, "/");
}

function joinPaths(basePath, methodPath) {
  return normalizePath(
    `${normalizePath(basePath)}/${normalizePath(methodPath)}`,
  );
}

function extractDiscoveredEndpoints(apiV1) {
  const discovered = [];
  const javaFiles = listJavaFiles(javaRoot);

  for (const filePath of javaFiles) {
    const content = fs.readFileSync(filePath, "utf8");
    const classPathMatch = content.match(
      /@Path\(([^)]+)\)[\s\S]*?public\s+class\s+/,
    );
    const classPathExpr = classPathMatch ? classPathMatch[1] : "";
    const classPath = decodePathExpression(classPathExpr, apiV1);

    const methodBlockPattern =
      /@(GET|POST|PUT|DELETE|PATCH)\b[\s\S]*?(?=@(?:GET|POST|PUT|DELETE|PATCH)\b|public\s+[\w<><\[\],.?]+\s+\w+\s*\()/g;
    for (const methodBlockMatch of content.matchAll(methodBlockPattern)) {
      const block = methodBlockMatch[0];
      const method = methodBlockMatch[1];
      const methodPathMatch = block.match(/@Path\(([^)]+)\)/);
      const methodPathExpr = methodPathMatch ? methodPathMatch[1] : "";
      const methodPath = decodePathExpression(methodPathExpr, apiV1);
      const fullPath = methodPathExpr
        ? joinPaths(classPath, methodPath)
        : classPath;
      if (!fullPath) {
        continue;
      }

      discovered.push({
        method,
        path: fullPath,
        source: path.relative(repoRoot, filePath),
        sourceGroup: path.basename(filePath, ".java"),
      });
    }
  }

  const uniqueByKey = new Map();
  for (const endpoint of discovered) {
    uniqueByKey.set(`${endpoint.method} ${endpoint.path}`, endpoint);
  }

  return Array.from(uniqueByKey.values()).sort((a, b) => {
    const left = `${a.path} ${a.method}`;
    const right = `${b.path} ${b.method}`;
    return left.localeCompare(right);
  });
}

function findFolder(collection, folderName) {
  const items = Array.isArray(collection.item) ? collection.item : [];
  return items.find(
    (entry) => entry?.name === folderName && Array.isArray(entry?.item),
  );
}

function ensureFolder(collection, folderName, description) {
  if (!Array.isArray(collection.item)) {
    collection.item = [];
  }
  let folder = findFolder(collection, folderName);
  if (!folder) {
    folder = { name: folderName, description, item: [] };
    collection.item.push(folder);
  } else if (!Array.isArray(folder.item)) {
    folder.item = [];
  }
  return folder;
}

function ensureChildFolder(parentFolder, folderName, description) {
  if (!Array.isArray(parentFolder.item)) {
    parentFolder.item = [];
  }
  let folder = parentFolder.item.find(
    (entry) => entry?.name === folderName && Array.isArray(entry?.item),
  );
  if (!folder) {
    folder = { name: folderName, description, item: [] };
    parentFolder.item.push(folder);
  } else if (!Array.isArray(folder.item)) {
    folder.item = [];
  }
  return folder;
}

function collectRequests(collection, out = []) {
  for (const item of collection.item ?? []) {
    if (Array.isArray(item?.item)) {
      collectRequests(item, out);
      continue;
    }
    if (item?.request?.method && item?.request?.url?.raw) {
      out.push(item);
    }
  }
  return out;
}

function toCollectionPathParam(segment) {
  const match = segment.match(/^\{([a-zA-Z0-9_]+)\}$/);
  if (!match) {
    return segment;
  }
  const name = match[1].replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase();
  return `{{${name}}}`;
}

function buildRawUrl(urlPath) {
  const pieces = urlPath
    .split("/")
    .filter(Boolean)
    .map((segment) => toCollectionPathParam(segment));
  return `{{base_url}}/${pieces.join("/")}`;
}

function buildPathArray(urlPath) {
  return urlPath
    .split("/")
    .filter(Boolean)
    .map((segment) => toCollectionPathParam(segment));
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

const endpointExampleTemplates = new Map([
  [
    "POST /api/v1/bootstrap/admin",
    {
      requestBody:
        '{\n  "username": "Admin Root",\n  "password": "AdminPassw0rd!"\n}',
      responses: [
        {
          name: "200 OK",
          status: "OK",
          code: 200,
          body: '{\n  "userId": "{{admin_user_id}}",\n  "username": "Admin Root"\n}',
        },
        {
          name: "409 Conflict",
          status: "Conflict",
          code: 409,
          body: '{\n  "type": "about:blank",\n  "title": "Bootstrap already completed",\n  "status": 409,\n  "detail": "Bootstrap already completed",\n  "code": "BOOTSTRAP_ALREADY_COMPLETED"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
  [
    "POST /api/v1/sessions",
    {
      requestBody:
        '{\n  "username": "Admin Root",\n  "password": "AdminPassw0rd!"\n}',
      responses: [
        {
          name: "200 OK",
          status: "OK",
          code: 200,
          body: '{\n  "sessionId": "{{session_id}}",\n  "token": "session-token-abc123"\n}',
        },
        {
          name: "401 Unauthorized",
          status: "Unauthorized",
          code: 401,
          body: '{\n  "type": "about:blank",\n  "title": "Unauthorized",\n  "status": 401,\n  "detail": "Invalid credentials",\n  "code": "UNAUTHORIZED"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
  [
    "POST /api/v1/sessions/logout",
    {
      requestBody: null,
      responses: [
        {
          name: "204 No Content",
          status: "No Content",
          code: 204,
          body: "",
          contentType: null,
        },
        {
          name: "401 Unauthorized",
          status: "Unauthorized",
          code: 401,
          body: '{\n  "type": "about:blank",\n  "title": "Unauthorized",\n  "status": 401,\n  "detail": "Session not authorized",\n  "code": "UNAUTHORIZED"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
  [
    "POST /api/v1/invitations",
    {
      requestBody:
        '{\n  "actorUserId": "{{admin_user_id}}",\n  "expiresAt": "2030-01-01T00:00:00Z"\n}',
      responses: [
        {
          name: "200 OK",
          status: "OK",
          code: 200,
          body: '{\n  "invitationId": "{{invitation_id}}",\n  "invitationToken": "invite-token-abc123"\n}',
        },
        {
          name: "403 Forbidden",
          status: "Forbidden",
          code: 403,
          body: '{\n  "type": "about:blank",\n  "title": "Invitation actor forbidden",\n  "status": 403,\n  "detail": "Invitation actor forbidden",\n  "code": "INVITATION_ACTOR_FORBIDDEN"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
  [
    "POST /api/v1/invitations/{invitationId}/revoke",
    {
      requestBody: '{\n  "actorUserId": "{{admin_user_id}}"\n}',
      responses: [
        {
          name: "204 No Content",
          status: "No Content",
          code: 204,
          body: "",
          contentType: null,
        },
        {
          name: "404 Not Found",
          status: "Not Found",
          code: 404,
          body: '{\n  "type": "about:blank",\n  "title": "Invitation not found",\n  "status": 404,\n  "detail": "Invitation not found",\n  "code": "INVITATION_NOT_FOUND"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
  [
    "POST /api/v1/invitations/redeem",
    {
      requestBody:
        '{\n  "invitationToken": "{{invitation_token}}",\n  "username": "member-user",\n  "password": "MemberPassw0rd!"\n}',
      responses: [
        {
          name: "200 OK",
          status: "OK",
          code: 200,
          body: '{\n  "userId": "11111111-1111-1111-1111-111111111111",\n  "username": "member-user"\n}',
        },
        {
          name: "422 Unprocessable Entity",
          status: "Unprocessable Entity",
          code: 422,
          body: '{\n  "type": "about:blank",\n  "title": "Invitation revoked",\n  "status": 422,\n  "detail": "Invitation revoked",\n  "code": "INVITATION_REVOKED"\n}',
          contentType: "application/problem+json",
        },
      ],
    },
  ],
]);

function getEndpointTemplate(method, pathValue) {
  const methodUpper = String(method || "").toUpperCase();
  const normalizedPath = normalizePath(pathValue);
  const direct = endpointExampleTemplates.get(
    `${methodUpper} ${normalizedPath}`,
  );
  if (direct) {
    return direct;
  }

  const canonicalizeTemplatePath = (value) =>
    normalizePath(value)
      .replace(/\{\{[^}]+\}\}/g, "{param}")
      .replace(/\{[^}]+\}/g, "{param}");

  const canonicalPath = canonicalizeTemplatePath(normalizedPath);
  for (const [
    templateKey,
    templateValue,
  ] of endpointExampleTemplates.entries()) {
    const [templateMethod, ...templatePathParts] = templateKey.split(" ");
    if (templateMethod !== methodUpper) {
      continue;
    }
    const templatePath = templatePathParts.join(" ");
    if (canonicalizeTemplatePath(templatePath) === canonicalPath) {
      return templateValue;
    }
  }

  return undefined;
}

function buildResponseExamples(endpoint, request, template) {
  if (!template?.responses?.length) {
    return [
      {
        name: "Example",
        originalRequest: {
          method: endpoint.method,
          header: request.header,
          body: request.body,
          url: request.url,
        },
        status: "OK",
        code: endpoint.method === "POST" ? 201 : 200,
        _postman_previewlanguage: "json",
        header: [{ key: "Content-Type", value: "application/json" }],
        body: "{}",
      },
    ];
  }

  return template.responses.map((responseTemplate) => ({
    name: responseTemplate.name,
    originalRequest: {
      method: endpoint.method,
      header: request.header,
      body: request.body,
      url: request.url,
    },
    status: responseTemplate.status,
    code: responseTemplate.code,
    _postman_previewlanguage:
      String(responseTemplate.body || "").trim().length > 0
        ? "json"
        : undefined,
    header:
      responseTemplate.contentType === null
        ? []
        : [
            {
              key: "Content-Type",
              value: responseTemplate.contentType || "application/json",
            },
          ],
    body: responseTemplate.body,
  }));
}

function buildFallbackRequestBody(endpoint) {
  const normalizedPath = normalizePath(endpoint.path);

  if (normalizedPath === "/api/v1/invitations") {
    return '{\n  "actorUserId": "{{admin_user_id}}",\n  "expiresAt": "2030-01-01T00:00:00Z"\n}';
  }
  if (normalizedPath === "/api/v1/invitations/redeem") {
    return '{\n  "invitationToken": "{{invitation_token}}",\n  "username": "member-user",\n  "password": "MemberPassw0rd!"\n}';
  }
  if (/\/api\/v1\/invitations\/\{[^/]+\}\/revoke$/.test(normalizedPath)) {
    return '{\n  "actorUserId": "{{admin_user_id}}"\n}';
  }
  if (normalizedPath === "/api/v1/bootstrap/admin") {
    return '{\n  "username": "Admin Root",\n  "password": "AdminPassw0rd!"\n}';
  }
  return '{\n  "exampleField": "replace_me"\n}';
}

function buildFallbackResponseExamples(endpoint, request) {
  const successCode = 200;
  const successBody = '{\n  "status": "ok"\n}';

  return [
    {
      name: `${successCode} Example`,
      originalRequest: {
        method: endpoint.method,
        header: request.header,
        body: request.body,
        url: request.url,
      },
      status: "OK",
      code: successCode,
      _postman_previewlanguage: "json",
      header: [{ key: "Content-Type", value: "application/json" }],
      body: successBody,
    },
  ];
}

function isEmptyJsonObjectBody(text) {
  return String(text || "").trim() === "{}";
}

function hasMeaningfulResponseBodies(responses) {
  if (!Array.isArray(responses) || responses.length === 0) {
    return false;
  }
  return responses.some((response) => {
    const bodyText = String(response?.body ?? "").trim();
    return bodyText.length > 0 && bodyText !== "{}";
  });
}

function canonicalizeRawUrl(rawUrl) {
  const text = String(rawUrl || "").trim();
  if (!text) {
    return "";
  }
  const withoutQuery = text.split("?")[0];
  return withoutQuery.replace(/\/+$/, "");
}

function requestKey(method, rawUrl) {
  return `${String(method || "").toUpperCase()} ${canonicalizeRawUrl(rawUrl)}`;
}

function extractPathFromRawUrl(rawUrl) {
  const value = String(rawUrl || "").trim();
  if (!value) {
    return "";
  }
  const withoutQuery = value.split("?")[0];
  const withoutBase = withoutQuery.replace(/^\{\{base_url\}\}/, "");
  return normalizePath(withoutBase);
}

function inferDomainFolderByPath(pathValue) {
  const normalized = normalizePath(pathValue);
  if (!normalized) {
    return "";
  }
  if (normalized === "/api/v1/ping" || normalized.startsWith("/q/health")) {
    return "Health";
  }
  if (
    normalized.startsWith("/api/v1/messages") ||
    /\/api\/v1\/conversations\/[^/]+\/messages$/.test(normalized)
  ) {
    return "Messaging";
  }
  return "";
}

function extractSourceGroupFromRequest(entry) {
  const description = String(entry?.request?.description || "");
  const match = description.match(/Auto-discovered from .*\/([^/]+)\.java\.?$/);
  if (match?.[1]) {
    return match[1];
  }
  return "Misc";
}

function buildDefaultEvent(method, expectedStatusCodes = [200, 201]) {
  const sortedCodes = Array.from(new Set(expectedStatusCodes)).sort(
    (a, b) => a - b,
  );
  const hasSingleCode = sortedCodes.length === 1;
  const expectationText = hasSingleCode
    ? `pm.response.to.have.status(${sortedCodes[0]});`
    : `pm.expect([${sortedCodes.join(", ")}]).to.include(pm.response.code);`;
  const assertionTitle = hasSingleCode
    ? `Status code is ${sortedCodes[0]}`
    : `Status code is one of ${sortedCodes.join(", ")}`;

  return [
    {
      listen: "test",
      script: {
        type: "text/javascript",
        exec: [
          `pm.test('${assertionTitle}', function () {`,
          `  ${expectationText}`,
          "});",
          "",
          "pm.test('Response is JSON when body is present', function () {",
          "  if (pm.response.text().trim().length > 0) {",
          "    pm.response.to.be.json;",
          "  }",
          "});",
        ],
      },
    },
  ];
}

function buildDiscoveredRequest(endpoint) {
  const isWrite =
    endpoint.method === "POST" ||
    endpoint.method === "PUT" ||
    endpoint.method === "PATCH";

  const request = {
    auth: { type: "noauth" },
    method: endpoint.method,
    header: [{ key: "Accept", value: "application/json" }],
    url: {
      raw: buildRawUrl(endpoint.path),
      host: ["{{base_url}}"],
      path: buildPathArray(endpoint.path),
    },
    description: `Auto-discovered from ${endpoint.source}.`,
  };

  const template = getEndpointTemplate(endpoint.method, endpoint.path);
  const templateStatusCodes = Array.isArray(template?.responses)
    ? template.responses
        .map((response) => response.code)
        .filter(Number.isInteger)
    : [];

  if (isWrite) {
    request.header.unshift({ key: "Content-Type", value: "application/json" });
    request.body = {
      mode: "raw",
      raw: template?.requestBody || buildFallbackRequestBody(endpoint),
      options: {
        raw: { language: "json" },
      },
    };
  }

  return {
    name: `${endpoint.method} ${endpoint.path}`,
    request,
    response:
      template?.responses?.length > 0
        ? buildResponseExamples(endpoint, request, template)
        : buildFallbackResponseExamples(endpoint, request),
    event: buildDefaultEvent(endpoint.method, templateStatusCodes),
  };
}

function applyEndpointTemplatesToExistingRequests(collection) {
  const allRequests = collectRequests(collection);
  let updatedCount = 0;

  for (const requestItem of allRequests) {
    const requestMethod = String(
      requestItem?.request?.method || "",
    ).toUpperCase();
    const requestPath = extractPathFromRawUrl(requestItem?.request?.url?.raw);
    const endpoint = { method: requestMethod, path: requestPath };
    const template = getEndpointTemplate(requestMethod, requestPath);
    const isDiscovered = String(
      requestItem?.request?.description || "",
    ).includes("Auto-discovered from ");

    if (!isDiscovered) {
      continue;
    }

    let changed = false;

    if (requestItem?.request?.body?.mode === "raw") {
      const currentBody = String(requestItem.request.body.raw || "");
      const nextBody =
        template?.requestBody || buildFallbackRequestBody(endpoint);
      if (isEmptyJsonObjectBody(currentBody) || template?.requestBody) {
        requestItem.request.body.raw = nextBody;
        changed = true;
      }
      if (!requestItem.request.body.options) {
        requestItem.request.body.options = { raw: { language: "json" } };
        changed = true;
      }
    }

    if (
      !Array.isArray(requestItem.response) ||
      requestItem.response.length === 0
    ) {
      requestItem.response = buildFallbackResponseExamples(
        endpoint,
        requestItem.request,
      );
      changed = true;
    }

    if (template?.responses?.length > 0) {
      requestItem.response = buildResponseExamples(
        endpoint,
        requestItem.request,
        template,
      );
      const templateStatusCodes = template.responses
        .map((response) => response.code)
        .filter(Number.isInteger);
      requestItem.event = buildDefaultEvent(requestMethod, templateStatusCodes);
      changed = true;
    } else if (!hasMeaningfulResponseBodies(requestItem.response)) {
      requestItem.response = buildFallbackResponseExamples(
        endpoint,
        requestItem.request,
      );
      changed = true;
    }

    if (changed) {
      requestItem.response = cloneJson(requestItem.response);
      updatedCount += 1;
    }
  }

  return updatedCount;
}

function buildHealthTemplate(name, rawPath, description, testName) {
  const pathArray = rawPath.split("/").filter(Boolean);
  return {
    name,
    request: {
      auth: { type: "noauth" },
      method: "GET",
      header: [{ key: "Accept", value: "application/json" }],
      url: {
        raw: `{{base_url}}${rawPath}`,
        host: ["{{base_url}}"],
        path: pathArray,
      },
      description,
    },
    response: [
      {
        name: "200 UP",
        originalRequest: {
          method: "GET",
          header: [{ key: "Accept", value: "application/json" }],
          url: {
            raw: `{{base_url}}${rawPath}`,
            host: ["{{base_url}}"],
            path: pathArray,
          },
        },
        status: "OK",
        code: 200,
        _postman_previewlanguage: "json",
        header: [{ key: "Content-Type", value: "application/json" }],
        body: '{\n  "status": "UP"\n}',
      },
    ],
    event: [
      {
        listen: "test",
        script: {
          type: "text/javascript",
          exec: [
            "pm.test('Status code is 200', function () {",
            "  pm.response.to.have.status(200);",
            "});",
            "",
            `pm.test('${testName}', function () {`,
            "  var jsonData = pm.response.json();",
            "  pm.expect(jsonData).to.have.property('status', 'UP');",
            "});",
          ],
        },
      },
    ],
  };
}

function ensureProtectedHealthRequests(collection) {
  const healthFolder = ensureFolder(
    collection,
    "Health",
    "Application and platform health endpoints for reachability and readiness.",
  );

  const protectedRequests = [
    buildHealthTemplate(
      "Health Live",
      "/q/health/live",
      "Quarkus liveness endpoint. Indicates process is alive.",
      "Health live is UP",
    ),
    buildHealthTemplate(
      "Health Ready",
      "/q/health/ready",
      "Quarkus readiness endpoint. Includes DB connectivity readiness checks.",
      "Health ready is UP (DB readiness)",
    ),
    buildHealthTemplate(
      "Health",
      "/q/health",
      "Quarkus aggregate health endpoint (includes readiness/liveness summary).",
      "Aggregate health is UP",
    ),
  ];

  const existingByRawUrl = new Map(
    (healthFolder.item ?? [])
      .filter((entry) => entry?.request?.url?.raw)
      .map((entry) => [entry.request.url.raw, entry]),
  );

  let addedCount = 0;
  for (const protectedRequest of protectedRequests) {
    const key = protectedRequest.request.url.raw;
    if (!existingByRawUrl.has(key)) {
      healthFolder.item.push(protectedRequest);
      addedCount += 1;
    }
  }

  return addedCount;
}

function mergeDiscoveredEndpoints(collection, endpoints) {
  const legacyFolder = findFolder(collection, "Discovered APIs");

  const nonDiscoveredRequests = [];
  for (const topLevelItem of collection.item ?? []) {
    if (topLevelItem === legacyFolder) {
      continue;
    }
    collectRequests(topLevelItem, nonDiscoveredRequests);
  }

  const nonDiscoveredKeys = new Set(
    nonDiscoveredRequests.map((entry) =>
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
    ),
  );

  const existingDiscoveredRequests = legacyFolder
    ? collectRequests(legacyFolder)
    : [];
  const reusableRequests = existingDiscoveredRequests.filter((entry) => {
    if (!entry?.request?.method || !entry?.request?.url?.raw) {
      return false;
    }
    return !nonDiscoveredKeys.has(
      requestKey(entry.request.method, entry.request.url.raw),
    );
  });

  let cleanedCount =
    existingDiscoveredRequests.length - reusableRequests.length;

  const reusableByKey = new Map(
    reusableRequests.map((entry) => [
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
      entry,
    ]),
  );

  const allRequests = collectRequests(collection);
  const existingKeys = new Set(
    allRequests.map((entry) =>
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
    ),
  );

  let addedCount = 0;
  let movedCount = 0;
  const grouped = new Map();

  const addToGroup = (groupName, requestItem) => {
    if (!grouped.has(groupName)) {
      grouped.set(groupName, []);
    }
    grouped.get(groupName).push(requestItem);
  };

  for (const [key, entry] of reusableByKey.entries()) {
    const domainFolderName = inferDomainFolderByPath(
      extractPathFromRawUrl(entry?.request?.url?.raw),
    );
    if (domainFolderName) {
      const domainFolder = ensureFolder(collection, domainFolderName, "");
      domainFolder.item.push(entry);
      movedCount += 1;
      existingKeys.add(key);
      continue;
    }

    const groupName = extractSourceGroupFromRequest(entry);
    addToGroup(groupName, entry);
    existingKeys.add(key);
  }

  for (const endpoint of endpoints) {
    const rawUrl = buildRawUrl(endpoint.path);
    const key = requestKey(endpoint.method, rawUrl);
    if (existingKeys.has(key)) {
      continue;
    }
    const requestItem = buildDiscoveredRequest(endpoint);
    const domainFolderName = inferDomainFolderByPath(endpoint.path);
    if (domainFolderName) {
      const domainFolder = ensureFolder(collection, domainFolderName, "");
      domainFolder.item.push(requestItem);
    } else {
      addToGroup(endpoint.sourceGroup || "Misc", requestItem);
    }
    existingKeys.add(key);
    addedCount += 1;
  }

  for (const groupName of Array.from(grouped.keys()).sort((a, b) =>
    a.localeCompare(b),
  )) {
    const groupFolder = ensureFolder(
      collection,
      groupName,
      `Auto-discovered endpoints from ${groupName}.java.`,
    );
    groupFolder.item = grouped.get(groupName);
  }

  if (legacyFolder && (collection.item ?? []).includes(legacyFolder)) {
    cleanedCount += 1;
    collection.item = (collection.item ?? []).filter(
      (item) => item !== legacyFolder,
    );
  }

  return { addedCount, movedCount, cleanedCount };
}

function run() {
  if (!fs.existsSync(collectionPath)) {
    fail(`Collection file not found: ${collectionPath}`);
  }

  const collection = readJson(collectionPath);
  const apiV1 = parseApiV1Constant();
  const discoveredEndpoints = extractDiscoveredEndpoints(apiV1);

  const healthAdded = ensureProtectedHealthRequests(collection);
  const { addedCount, movedCount, cleanedCount } = mergeDiscoveredEndpoints(
    collection,
    discoveredEndpoints,
  );
  const templatedCount = applyEndpointTemplatesToExistingRequests(collection);

  if (
    healthAdded === 0 &&
    addedCount === 0 &&
    movedCount === 0 &&
    cleanedCount === 0 &&
    templatedCount === 0
  ) {
    console.log("Postman discovery completed: no collection changes needed.");
    return;
  }

  writeJson(collectionPath, collection);
  console.log(
    `Postman discovery completed: added ${healthAdded} protected health request(s), added ${addedCount} discovered API request(s), moved ${movedCount} discovered request(s) into domain folders, cleaned ${cleanedCount} duplicate/stale discovered request(s), refreshed ${templatedCount} endpoint example template(s).`,
  );
}

run();
