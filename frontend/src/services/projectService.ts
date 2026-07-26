import api from "../api/api";
import type { Project } from "../types/project";

export async function getProjects(): Promise<Project[]> {
  const response = await api.get<Project[]>("/projects");

  return response.data;
}
