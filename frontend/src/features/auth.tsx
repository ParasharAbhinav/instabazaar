import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Link,
  Navigate,
  Outlet,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { ApiError, getCsrf, request, resetCsrf, write } from "../api/client";
import type { Role, User } from "../api/contracts";
import { ActionForm, Field, QueryState } from "../components/ui";
import { returnPath } from "../utils/validation";
export const demo = import.meta.env.VITE_DEMO_MODE === "true";
export function useUser() {
  return useQuery<User | null>({
    queryKey: ["session"],
    queryFn: async () => {
      if (demo) return null;
      try {
        return await request<User>("/me");
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    retry: false,
    staleTime: 60000,
  });
}
export function SessionEvents() {
  const client = useQueryClient();
  useEffect(() => {
    const clear = () => {
      client.clear();
      client.setQueryData(["session"], null);
    };
    window.addEventListener("session-expired", clear);
    return () => window.removeEventListener("session-expired", clear);
  }, [client]);
  return null;
}
export function Guard({ role }: { role?: Role }) {
  const q = useUser();
  const loc = useLocation();
  if (q.isPending || q.error) return <QueryState query={q}>{null}</QueryState>;
  if (!q.data)
    return (
      <Navigate
        to={"/login?next=" + encodeURIComponent(loc.pathname + loc.search)}
        replace
      />
    );
  if (!q.data.enabled || (role && q.data.role !== role))
    return (
      <section className="empty">
        <h1>Access unavailable</h1>
        <p>
          This page requires an enabled {role?.toLowerCase() || "user"} account.
        </p>
        <Link to="/">Return to discovery</Link>
      </section>
    );
  return <Outlet />;
}
export function AuthPage({ register = false }: { register?: boolean }) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const [params] = useSearchParams();
  return (
    <div className="auth-shell">
      <aside>
        <span className="eyebrow">A LITTLE DISCOVERY. A LOT OF YOU.</span>
        <h1>
          Your next favourite
          <br />
          is waiting here.
        </h1>
        <p>
          Independent labels. Thoughtful finds.
          <br />A whole new way to discover your style.
        </p>
        <div className="auth-art">
          iB<span>✦</span>
        </div>
      </aside>
      <section>
        <Link className="wordmark" to="/">
          insta<span>bazaar</span>
          <sup>✦</sup>
        </Link>
        <h2>
          {register ? "Find your people. Find your style." : "Welcome back."}
        </h2>
        <p>
          {register
            ? "Create an account to save finds or open your storefront."
            : "Sign in to pick up where you left off."}
        </p>
        {params.has("registered") && (
          <p className="notice success">
            Account created. Sign in to continue.
          </p>
        )}
        {demo ? (
          <p className="notice">
            This is a public catalogue preview. Connect the Spring Boot API to
            use accounts.
          </p>
        ) : (
          <ActionForm
            label={register ? "Create account" : "Sign in"}
            submit={async (f) => {
              const body = register
                ? {
                    name: String(f.get("name")).trim(),
                    email: String(f.get("email")).trim().toLowerCase(),
                    password: String(f.get("password")),
                    role: String(f.get("role")),
                  }
                : {
                    email: String(f.get("email")).trim().toLowerCase(),
                    password: String(f.get("password")),
                  };
              if (register) {
                await write("/auth/register", "POST", body);
                navigate("/login?registered=1");
              } else {
                const signedIn = await write<User>("/auth/login", "POST", body);
                resetCsrf();
                client.clear();
                client.setQueryData(["session"], signedIn);
                await getCsrf();
                navigate(returnPath(params.get("next")), { replace: true });
              }
            }}
          >
            {register && (
              <Field label="Your name" name="name" required maxLength={100} />
            )}
            <label className="field">
              <span>Email address</span>
              <input
                name="email"
                type="email"
                required
                maxLength={254}
                autoComplete="email"
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                name="password"
                type="password"
                required
                minLength={register ? 12 : 1}
                maxLength={128}
                autoComplete={register ? "new-password" : "current-password"}
              />
            </label>
            {register && (
              <>
                <Field label="I’m here to" name="role">
                  <select name="role">
                    <option value="BUYER">Discover and save fashion</option>
                    <option value="SELLER">Create a seller storefront</option>
                  </select>
                </Field>
                <small>Use 12–128 characters for your password.</small>
              </>
            )}
          </ActionForm>
        )}
        <p>
          {register ? "Already part of the community?" : "New around here?"}{" "}
          <Link to={register ? "/login" : "/register"}>
            {register ? "Sign in" : "Create an account"}
          </Link>
        </p>
      </section>
    </div>
  );
}
export function Profile() {
  const user = useUser().data!;
  const client = useQueryClient();
  const navigate = useNavigate();
  return (
    <section className="narrow">
      <span className="eyebrow">YOUR ACCOUNT</span>
      <h1>A little about you.</h1>
      <p>
        {user.email} · {user.role.toLowerCase()}
      </p>
      <ActionForm
        key={user.version}
        submit={(f) =>
          write("/me", "PUT", {
            name: String(f.get("name")).trim(),
            version: user.version,
          })
        }
      >
        <Field
          label="Display name"
          name="name"
          value={user.name}
          required
          maxLength={100}
        />
      </ActionForm>
      <Link to="/profile/reviews">Manage my reviews →</Link>
      <hr />
      <ActionForm
        label="Sign out"
        success="Signed out."
        submit={async () => {
          await write("/auth/logout", "POST");
          resetCsrf();
          client.clear();
          client.setQueryData(["session"], null);
          await getCsrf();
          navigate("/");
        }}
      >
        <p>End your session on this device.</p>
      </ActionForm>
    </section>
  );
}
