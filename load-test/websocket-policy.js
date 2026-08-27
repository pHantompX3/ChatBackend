import { check } from "k6";
import { WebSocket } from "k6/websockets";

export const options = {
  insecureSkipTLSVerify: __ENV.WL_CHAT_LOAD_INSECURE_TLS === "true",
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ["rate==1"],
  },
};

const wsBaseUrl = __ENV.WL_CHAT_LOAD_WS_BASE_URL;
const allowedOrigin = __ENV.WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN;

export function setup() {
  if (!wsBaseUrl || !allowedOrigin) {
    throw new Error(
      "WL_CHAT_LOAD_WS_BASE_URL and WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN are required",
    );
  }
}

export default function () {
  expectClose("disallowed Origin", `${wsBaseUrl}/api/v1/ws`, "https://untrusted.invalid", 4403);
  expectClose("missing credential", `${wsBaseUrl}/api/v1/ws`, allowedOrigin, 4401);
  expectClose(
    "disabled query token",
    `${wsBaseUrl}/api/v1/ws?token=sentinel-query-token`,
    allowedOrigin,
    4401,
  );
}

function expectClose(name, url, origin, expectedCode) {
  const socket = new WebSocket(url, [], { headers: { Origin: origin } });
  const timeout = setTimeout(() => socket.close(), 5000);

  socket.addEventListener("close", (event) => {
    clearTimeout(timeout);
    check(event, { [`${name} closes with ${expectedCode}`]: (value) => value.code === expectedCode });
  });
}
