import api, { API_BASE_URL } from "../api/api";
import type {
  DemoScenarioKey,
  DemoScenarioRunResponse,
  LiveTransactionEvent,
  SimulationScenario,
  SimulationStatus,
} from "../types/simulation";

export async function getSimulationStatus(): Promise<SimulationStatus> {
  const response = await api.get<SimulationStatus>("/simulation/status");
  return response.data;
}

export async function startSimulation(
  scenario: SimulationScenario,
): Promise<SimulationStatus> {
  const response = await api.post<SimulationStatus>(
    "/simulation/start",
    { scenario },
  );
  return response.data;
}

export async function stopSimulation(): Promise<SimulationStatus> {
  const response = await api.post<SimulationStatus>("/simulation/stop");
  return response.data;
}

export async function generateScenario(
  scenario: SimulationScenario,
): Promise<SimulationStatus> {
  const response = await api.post<SimulationStatus>(
    `/simulation/scenario/${scenario}`,
  );
  return response.data;
}

export function getSimulationLiveUrl(): string {
  return `${API_BASE_URL}/simulation/live`;
}

const DEMO_ENDPOINTS: Record<DemoScenarioKey, string> = {
  structuring: "/simulation/demos/structuring",
  "high-risk-wire": "/simulation/demos/high-risk-wire",
  "money-mule": "/simulation/demos/money-mule",
  normal: "/simulation/demos/normal",
};

export async function runDemoScenario(
  scenario: DemoScenarioKey,
): Promise<DemoScenarioRunResponse> {
  const response = await api.post<DemoScenarioRunResponse>(
    DEMO_ENDPOINTS[scenario],
  );
  return response.data;
}

export type { LiveTransactionEvent, DemoScenarioRunResponse };
