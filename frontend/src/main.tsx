import React, { Suspense, lazy, useEffect } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  BrowserRouter,
  Routes,
  Route,
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { Heart, Search, UserRound, ArrowUpRight } from "lucide-react";
import { ApiError } from "./api/client";
import { ErrorBoundary, Empty } from "./components/ui";
import {
  AuthPage,
  Guard,
  Profile,
  SessionEvents,
  demo,
  useUser,
} from "./features/auth";
import {
  Home,
  Catalog,
  ProductPage,
  Storefront,
  Wishlist,
  MyReviews,
} from "./features/catalog";
import "./styles.css";
const SampleProject = lazy(() => import('./features/sample').then(m => ({ default: m.SampleProject })));
const SellerDashboard = lazy(() =>
  import("./features/seller").then((m) => ({ default: m.SellerDashboard })),
);
const StoreEditor = lazy(() =>
  import("./features/seller").then((m) => ({ default: m.StoreEditor })),
);
const ProductEditor = lazy(() =>
  import("./features/seller").then((m) => ({ default: m.ProductEditor })),
);
const AdminDashboard = lazy(() =>
  import("./features/admin").then((m) => ({ default: m.AdminDashboard })),
);
const AdminList = lazy(() =>
  import("./features/admin").then((m) => ({ default: m.AdminList })),
);
const client = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30000,
      retry: (n, e) => n < 1 && !(e instanceof ApiError && e.status < 500),
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
});
function ScrollReset() {
  const loc = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
    document.title =
      "InstaBazaar — " +
      (loc.pathname === "/"
        ? "Discover your kind of style"
        : loc.pathname.split("/").filter(Boolean).join(" · "));
    document.getElementById("main")?.focus({ preventScroll: true });
  }, [loc.pathname, loc.search]);
  return null;
}
function Layout() {
  const user = useUser();
  const navigate = useNavigate();
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      {demo && (
        <div className="demo-banner">
          Catalogue preview · illustrative sample listings · accounts require
          the Spring Boot API
        </div>
      )}
      <div className="announcement">
        A world of independent style. A little closer to you.{" "}
        <span>Discover something different ↗</span>
      </div>
      <header className="header">
        <Link className="wordmark" to="/">
          insta<span>bazaar</span>
          <sup>✦</sup>
        </Link>
        <nav aria-label="Main navigation">
          <NavLink to="/products">Discover</NavLink>
          <NavLink to="/sample">Try sample</NavLink>
          <NavLink to="/products?sort=newest">New finds</NavLink>
          <NavLink
            to={
              user.data?.role === "SELLER" ? "/seller/dashboard" : "/register"
            }
          >
            For sellers <ArrowUpRight size={13} />
          </NavLink>
        </nav>
        <form
          className="header-search"
          onSubmit={(e) => {
            e.preventDefault();
            navigate(
              "/products?q=" +
                encodeURIComponent(
                  String(new FormData(e.currentTarget).get("q") || ""),
                ),
            );
          }}
        >
          <Search size={16} />
          <input
            name="q"
            aria-label="Search the collection"
            maxLength={200}
            placeholder="Find your kind of style"
          />
        </form>
        <div className="header-actions">
          <Link to="/wishlist" aria-label="Wishlist">
            <Heart size={20} />
          </Link>
          <Link
            to={user.data ? "/profile" : "/login"}
            aria-label={user.data ? "My account" : "Sign in"}
          >
            <UserRound size={20} />
            <span>{user.data ? user.data.name.split(" ")[0] : "Sign in"}</span>
          </Link>
        </div>
      </header>
      {user.data?.role === "SELLER" && (
        <nav className="workspace-nav" aria-label="Seller navigation">
          <NavLink to="/seller/dashboard">Seller studio</NavLink>
          <NavLink to="/seller/store">Storefront</NavLink>
          <NavLink to="/seller/products/new">Add product</NavLink>
        </nav>
      )}
      {user.data?.role === "ADMIN" && (
        <nav className="workspace-nav" aria-label="Admin navigation">
          {["", "users", "products", "reviews", "reports"].map((x) => (
            <NavLink key={x} to={"/admin" + (x ? "/" + x : "")} end>
              {x || "Overview"}
            </NavLink>
          ))}
        </nav>
      )}
      <main id="main" tabIndex={-1} className="container">
        <ScrollReset />
        <Suspense
          fallback={
            <p className="empty" role="status">
              Loading your page…
            </p>
          }
        >
          <Outlet />
        </Suspense>
      </main>
      <footer>
        <div>
          <Link className="wordmark" to="/">
            insta<span>bazaar</span>
            <sup>✦</sup>
          </Link>
          <p>
            Independent finds.
            <br />A style that’s entirely yours.
          </p>
        </div>
        <div>
          <strong>Find your way</strong>
          <Link to="/products">Discover the collection</Link>
          <Link to="/wishlist">Your wishlist</Link>
          <Link to="/register">Open a storefront</Link>
        </div>
        <div>
          <strong>A more personal marketplace.</strong>
          <p>
            Discover, save, and connect directly with sellers.
            <br />
            Purchases are arranged independently with each store.
          </p>
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} InstaBazaar</span>
          <span>Made for the joy of discovery. ✦</span>
        </div>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={client}>
        <BrowserRouter>
          <SessionEvents />
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Home />} />
              <Route path="sample" element={<SampleProject />} />
              <Route path="products" element={<Catalog />} />
              <Route path="categories/:id" element={<Catalog />} />
              <Route path="products/:id" element={<ProductPage />} />
              <Route path="sellers/:id" element={<Storefront />} />
              <Route path="login" element={<AuthPage />} />
              <Route path="register" element={<AuthPage register />} />
              <Route element={<Guard />}>
                <Route path="profile" element={<Profile />} />
                <Route path="profile/reviews" element={<MyReviews />} />
                <Route path="wishlist" element={<Wishlist />} />
              </Route>
              <Route element={<Guard role="SELLER" />}>
                <Route path="seller/dashboard" element={<SellerDashboard />} />
                <Route path="seller/store" element={<StoreEditor />} />
                <Route path="seller/products/new" element={<ProductEditor />} />
                <Route
                  path="seller/products/:id/edit"
                  element={<ProductEditor />}
                />
              </Route>
              <Route element={<Guard role="ADMIN" />}>
                <Route path="admin" element={<AdminDashboard />} />
                {(["users", "products", "reviews", "reports"] as const).map(
                  (kind) => (
                    <Route
                      key={kind}
                      path={"admin/" + kind}
                      element={<AdminList kind={kind} />}
                    />
                  ),
                )}
              </Route>
              <Route
                path="*"
                element={
                  <Empty
                    title="A little off the beaten path."
                    detail="We couldn’t find that page."
                    href="/"
                    label="Back to discovery"
                  />
                }
              />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>,
);
