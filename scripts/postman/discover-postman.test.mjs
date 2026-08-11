import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import {
  extractDiscoveredEndpoints,
  mergeDiscoveredEndpoints,
  parseApiV1Constant,
} from "./discover-postman.mjs";

test("extractDiscoveredEndpoints finds class-level and method-level paths", () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "postman-discovery-"));
  const javaDir = path.join(tempDir, "src", "main", "java", "com", "example");
  fs.mkdirSync(javaDir, { recursive: true });
  const sourcePath = path.join(javaDir, "SessionResource.java");
  fs.writeFileSync(
    sourcePath,
    `package com.example;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path(ApiRoutes.API_V1 + "/sessions")
public class SessionResource {
  @POST
  public SessionLoginResponse login() {
    return null;
  }

  @POST
  @Path("/logout")
  public void logout() {}
}
`,
    "utf8",
  );

  const apiRoutesPath = path.join(
    tempDir,
    "src",
    "main",
    "java",
    "com",
    "example",
    "ApiRoutes.java",
  );
  fs.mkdirSync(path.dirname(apiRoutesPath), { recursive: true });
  fs.writeFileSync(
    apiRoutesPath,
    'class ApiRoutes { public static final String API_V1 = "/api/v1"; }',
    "utf8",
  );

  const prevCwd = process.cwd();
  process.chdir(tempDir);
  try {
    const apiV1 = parseApiV1Constant(
      path.join(
        tempDir,
        "src",
        "main",
        "java",
        "com",
        "example",
        "ApiRoutes.java",
      ),
    );
    const endpoints = extractDiscoveredEndpoints(
      apiV1,
      path.join(tempDir, "src", "main", "java"),
    );
    const paths = endpoints.map((entry) => `${entry.method} ${entry.path}`);
    assert.deepEqual(paths, [
      "POST /api/v1/sessions",
      "POST /api/v1/sessions/logout",
    ]);
  } finally {
    process.chdir(prevCwd);
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test("mergeDiscoveredEndpoints keeps existing and newly discovered requests in same source group", () => {
  const collection = {
    info: { name: "WL-Chat" },
    item: [
      {
        name: "SessionResource",
        description: "Auto-discovered endpoints from SessionResource.java.",
        item: [
          {
            name: "POST /api/v1/sessions",
            request: {
              method: "POST",
              url: {
                raw: "{{base_url}}/api/v1/sessions",
              },
              description:
                "Auto-discovered from src/main/java/com/wayden/messenger/session/api/SessionResource.java.",
            },
          },
        ],
      },
    ],
  };

  const endpoints = [
    {
      method: "POST",
      path: "/api/v1/sessions",
      source:
        "src/main/java/com/wayden/messenger/session/api/SessionResource.java",
      sourceGroup: "SessionResource",
    },
    {
      method: "POST",
      path: "/api/v1/sessions/logout",
      source:
        "src/main/java/com/wayden/messenger/session/api/SessionResource.java",
      sourceGroup: "SessionResource",
    },
  ];

  const result = mergeDiscoveredEndpoints(collection, endpoints);
  assert.equal(result.addedCount, 1);

  const sessionFolder = collection.item.find(
    (entry) => entry.name === "SessionResource",
  );
  assert.ok(sessionFolder);
  assert.deepEqual(sessionFolder.item.map((entry) => entry.name).sort(), [
    "POST /api/v1/sessions",
    "POST /api/v1/sessions/logout",
  ]);
});
