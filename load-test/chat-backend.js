import http from "k6/http";
import ws from "k6/ws";
import { check, sleep } from "k6";

const phase = __ENV.WL_CHAT_LOAD_PHASE || "characterization";
const vus = phase === "regression" ? 10 : 3;
const duration = phase === "regression" ? "60s" : "30s";

export const options = {
  insecureSkipTLSVerify: __ENV.WL_CHAT_LOAD_INSECURE_TLS === "true",
  vus,
  duration,
  thresholds:
    phase === "regression"
      ? {
          http_req_failed: ["rate<0.01"],
          http_req_duration: ["p(95)<500"],
          checks: ["rate>0.99"],
        }
      : {},
};

const baseUrl = __ENV.WL_CHAT_LOAD_BASE_URL;
const wsBaseUrl = __ENV.WL_CHAT_LOAD_WS_BASE_URL;
const token = __ENV.WL_CHAT_LOAD_TOKEN;
const conversationId = __ENV.WL_CHAT_LOAD_CONVERSATION_ID;

export function setup() {
  if (!baseUrl || !wsBaseUrl || !token || !conversationId) {
    throw new Error(
      "WL_CHAT_LOAD_BASE_URL, WL_CHAT_LOAD_WS_BASE_URL, WL_CHAT_LOAD_TOKEN, and WL_CHAT_LOAD_CONVERSATION_ID are required",
    );
  }
}

export default function () {
  const response = http.get(
    `${baseUrl}/api/v1/conversations/${conversationId}/messages?afterSequence=0&limit=50`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  check(response, { "history returned 200": (result) => result.status === 200 });

  if (__ITER % 10 === 0) {
    const socketResponse = ws.connect(
      `${wsBaseUrl}/api/v1/ws`,
      { headers: { Authorization: `Bearer ${token}` } },
      (socket) => {
        socket.on("open", () => socket.send(JSON.stringify({ action: "ping" })));
        socket.on("message", (payload) => {
          if (payload === '{"type":"pong"}') {
            socket.close();
          }
        });
        socket.setTimeout(() => socket.close(), 3000);
      },
    );
    check(socketResponse, { "websocket upgraded": (result) => result && result.status === 101 });
  }

  sleep(1);
}
