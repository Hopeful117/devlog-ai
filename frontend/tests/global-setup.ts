import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const KEYCLOAK_URL = process.env.PLAYWRIGHT_KEYCLOAK_URL ?? 'http://localhost:18084';
const BACKEND_URL = process.env.PLAYWRIGHT_BACKEND_URL ?? 'http://localhost:18080';
const REALM = 'devlog';
const CLIENT_ID = 'devlog-core';
const USERNAME = process.env.PLAYWRIGHT_E2E_USERNAME ?? 'playwright-e2e';
const PASSWORD = process.env.PLAYWRIGHT_E2E_PASSWORD ?? 'playwright-e2e-password';

interface TokenResponse {
  access_token: string;
}

async function formPost<T>(
  url: string,
  values: Record<string, string>,
): Promise<Response & { json(): Promise<T> }> {
  return fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(values),
  }) as Promise<Response & { json(): Promise<T> }>;
}

async function waitFor(url: string): Promise<void> {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.status < 500) return;
    } catch {
      // The Compose services may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`E2E service did not become reachable: ${url}`);
}

async function getAdminToken(): Promise<string> {
  const response = await formPost<TokenResponse>(
    `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
    {
      client_id: 'admin-cli',
      grant_type: 'password',
      username: process.env.PLAYWRIGHT_KEYCLOAK_ADMIN ?? 'admin',
      password: process.env.PLAYWRIGHT_KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
    },
  );
  if (!response.ok) throw new Error(`Keycloak admin token failed: ${response.status}`);
  return (await response.json()).access_token;
}

async function ensureUser(adminToken: string): Promise<void> {
  const response = await fetch(`${KEYCLOAK_URL}/admin/realms/${REALM}/users`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${adminToken}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      username: USERNAME,
      enabled: true,
      firstName: 'Playwright',
      lastName: 'E2E',
      email: `${USERNAME}@example.test`,
      emailVerified: true,
      credentials: [{ type: 'password', value: PASSWORD, temporary: false }],
    }),
  });
  if (!response.ok && response.status !== 409) {
    throw new Error(`Keycloak E2E user creation failed: ${response.status}`);
  }
  const usersResponse = await fetch(
    `${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${encodeURIComponent(USERNAME)}`,
    { headers: { authorization: `Bearer ${adminToken}` } },
  );
  if (!usersResponse.ok)
    throw new Error(`Keycloak E2E user lookup failed: ${usersResponse.status}`);
  const users = (await usersResponse.json()) as { id: string; username: string }[];
  const user = users.find((candidate) => candidate.username === USERNAME);
  if (!user) throw new Error('Keycloak E2E user was not found after creation');
  const userResponse = await fetch(`${KEYCLOAK_URL}/admin/realms/${REALM}/users/${user.id}`, {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${adminToken}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      username: USERNAME,
      enabled: true,
      firstName: 'Playwright',
      lastName: 'E2E',
      email: `${USERNAME}@example.test`,
      emailVerified: true,
    }),
  });
  if (!userResponse.ok) throw new Error(`Keycloak E2E user setup failed: ${userResponse.status}`);
  const passwordResponse = await fetch(
    `${KEYCLOAK_URL}/admin/realms/${REALM}/users/${user.id}/reset-password`,
    {
      method: 'PUT',
      headers: {
        authorization: `Bearer ${adminToken}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ type: 'password', value: PASSWORD, temporary: false }),
    },
  );
  if (!passwordResponse.ok) {
    throw new Error(`Keycloak E2E password setup failed: ${passwordResponse.status}`);
  }
}

async function getUserToken(): Promise<string> {
  const response = await formPost<TokenResponse>(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    { client_id: CLIENT_ID, grant_type: 'password', username: USERNAME, password: PASSWORD },
  );
  if (!response.ok) throw new Error(`Keycloak E2E user token failed: ${response.status}`);
  return (await response.json()).access_token;
}

async function ensureProject(token: string): Promise<void> {
  const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json' };
  const projectsResponse = await fetch(`${BACKEND_URL}/api/v1/projects`, { headers });
  if (!projectsResponse.ok)
    throw new Error(`Project fixture lookup failed: ${projectsResponse.status}`);
  const projects = (await projectsResponse.json()) as { slug?: string }[];
  if (projects.some((project) => project.slug === 'devlog-ai')) return;
  const response = await fetch(`${BACKEND_URL}/api/v1/projects`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ name: 'devlog-ai', description: 'Playwright Docker fixture' }),
  });
  if (!response.ok && response.status !== 409) {
    throw new Error(`Project fixture creation failed: ${response.status}`);
  }
}

export default async function globalSetup(): Promise<void> {
  if (process.env.PLAYWRIGHT_SERVE === 'true') return;

  await waitFor(`${KEYCLOAK_URL}/realms/${REALM}/.well-known/openid-configuration`);
  await waitFor(`${BACKEND_URL}/actuator/health`);
  const adminToken = await getAdminToken();
  await ensureUser(adminToken);
  const userToken = await getUserToken();
  await ensureProject(userToken);

  const outputPath = join(process.cwd(), 'test-results', 'e2e-auth.json');
  mkdirSync(join(process.cwd(), 'test-results'), { recursive: true });
  writeFileSync(outputPath, JSON.stringify({ accessToken: userToken }), { mode: 0o600 });
}
