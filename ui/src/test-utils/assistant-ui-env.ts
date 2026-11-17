import { ReadableStream, TransformStream, WritableStream } from "node:stream/web";

// jsdom lacks web streams and Response; assistant-ui's stream module needs them when it loads, and
// subclasses Response at import time. The session view never builds a response, so a bare class does.
Object.assign(globalThis, { ReadableStream, TransformStream, WritableStream });
if (typeof globalThis.Response === "undefined") {
  Object.assign(globalThis, { Response: class Response {} });
}
