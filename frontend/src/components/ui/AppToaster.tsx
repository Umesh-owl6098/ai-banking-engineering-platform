import { Toaster } from "react-hot-toast";

export default function AppToaster() {
  return (
    <Toaster
      position="bottom-right"
      toastOptions={{
        duration: 4000,
        style: {
          fontSize: "0.875rem",
          borderRadius: 8,
          border: "1px solid #e2e8f0",
          boxShadow: "0 4px 12px rgba(15, 23, 42, 0.08)",
        },
        success: {
          iconTheme: { primary: "#15803d", secondary: "#ffffff" },
        },
        error: {
          iconTheme: { primary: "#b91c1c", secondary: "#ffffff" },
        },
      }}
    />
  );
}
