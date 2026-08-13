import { NextResponse } from "next/server";

// Docker healthcheck target (infra/docker-compose.yml, infra/docker-compose.prod.yml) -
// only confirms the Next.js server itself is up, no backend/DB dependency to check.
export function GET() {
  return NextResponse.json({ status: "ok" });
}
