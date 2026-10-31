import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { App } from "./App";

describe("App", () => {
  beforeAll(() => {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows the booking experience", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((path: string) => Promise.resolve(
      path === "/api/v1/auth/me"
        ? { ok: false, status: 401, json: async () => ({ code: "UNAUTHENTICATED" }) }
        : { ok: true, status: 200, json: async () => [] },
    )));

    render(<App />);
    expect(screen.getByRole("heading", { name: /healthcare that makes room for you/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Services will appear here when the clinic is ready.")).toBeInTheDocument();
    });
  });

  it("keeps the booking page usable if a background request fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network")));

    render(<App />);
    await waitFor(() => expect(screen.getByText("A booking flow that feels human.")).toBeInTheDocument());
  });
});
