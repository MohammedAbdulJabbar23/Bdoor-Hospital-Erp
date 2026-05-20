import { request as pwRequest, APIRequestContext } from '@playwright/test';

const API = 'http://localhost:8080/api';

export async function getToken(username: string, password = username): Promise<string> {
  const ctx = await pwRequest.newContext();
  const res = await ctx.post(`${API}/auth/login`, { data: { username, password } });
  const body = await res.json();
  await ctx.dispose();
  return body.token as string;
}

export async function authedContext(username: string, password = username): Promise<APIRequestContext> {
  const token = await getToken(username, password);
  return await pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
}

export async function getDoctorIdByUserName(api: APIRequestContext, fullName: string): Promise<string> {
  const res = await api.get(`${API}/doctors`);
  const list = await res.json();
  const match = list.find((d: any) => d.fullName === fullName);
  if (!match) throw new Error(`Doctor with name ${fullName} not found`);
  return match.id;
}

export async function getVisitsByPatient(api: APIRequestContext, patientId: string): Promise<any[]> {
  const res = await api.get(`${API}/visits?page=0&size=20`);
  const body = await res.json();
  return (body.content as any[]).filter((v) => v.patientId === patientId);
}
