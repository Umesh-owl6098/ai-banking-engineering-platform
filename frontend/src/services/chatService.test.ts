import { describe, expect, it, vi } from "vitest";

import { processSseEvent } from "./chatService";

describe("processSseEvent", () => {
  it("handles a successful complete event", () => {
    const onComplete = vi.fn();

    const result = processSseEvent(
      'event: complete\ndata: {"assistantMessageId":"abc"}',
      {
        onToken: vi.fn(),
        onComplete,
      },
    );

    expect(result.type).toBe("complete");
    expect(onComplete).toHaveBeenCalledWith({
      assistantMessageId: "abc",
    });
  });

  it("handles structured error events", () => {
    const onError = vi.fn();

    const result = processSseEvent(
      'event: error\ndata: {"message":"Upstream failed","code":"UPSTREAM_ERROR"}',
      {
        onToken: vi.fn(),
        onError,
      },
    );

    expect(result.type).toBe("error");
    expect(onError).toHaveBeenCalledWith(
      "Upstream failed",
      "UPSTREAM_ERROR",
    );
  });

  it("reports malformed stream data without throwing", () => {
    const onError = vi.fn();

    const result = processSseEvent(
      "event: token\ndata: not-json",
      {
        onToken: vi.fn(),
        onError,
      },
    );

    expect(result.type).toBe("error");

    if (result.type === "error") {
      expect(result.code).toBe("MALFORMED_STREAM");
    }
    expect(onError).toHaveBeenCalled();
  });

  it("streams token events to the callback", () => {
    const onToken = vi.fn();

    processSseEvent(
      'event: token\ndata: {"token":"Hello"}',
      {
        onToken,
      },
    );

    expect(onToken).toHaveBeenCalledWith("Hello");
  });
});
