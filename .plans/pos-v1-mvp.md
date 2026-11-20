# Plan: Single-Store Counter POS (v1)

## Objective
Build a pragmatic, server-rendered Point of Sale for counter retail (catalog, cart, split payments, tickets, returns, cash shifts, stock ledger, and basic reports) with Spring Boot 4, Postgres, Thymeleaf, HTMX, and Alpine.js.

## Architecture & Simplicity Check
- **DOP & Vertical Slices:** Flat feature packages (`auth`, `catalog`, `shift`, `sale`, `payment`, `ticket`, `returns`, `inventory`, `report`) using Java 25 `record`s for DTOs and pure pricing/shift-variance math, `long` minor units (`*_cents`) for money, and direct Spring MVC controllers + JPA repositories (no `ServiceImpl`/MapStruct layers).
- **Dependencies Accepted:** `flyway-core` + `flyway-database-postgresql` for SQL migrations; locally vendored HTMX (v2 stable; swappable for v4), Alpine.js, and plain CSS under `static/` (zero Node/npm/CDN).
- **Explicit Non-Goals (v1):** Restaurant tables/kitchen tickets, variant matrices, multi-branch (`sucursal`) sync, live payment-gateway APIs, and raw ESC/POS thermal drivers.
- **Task Tracking:** Managed in `.depenta/` via `depenta` CLI (replacing `.issues/*.yaml` and `gh` issue sync); each task maps to one small PR.

## Execution Graph
See `depenta list --topo` for live DAG status (`--after` prerequisites, `--rel` context links):
- [ ] Foundation: Flyway + Postgres Docker Compose baseline
- [ ] UI Shell: Base layout, vendored HTMX + Alpine.js, CSRF, plain CSS
- [ ] Auth: Spring Security ADMIN/CASHIER login + quick cashier PIN switch
- [ ] Catalog: Product CRUD, barcode/SKU lookup, HTMX search
- [ ] Shifts: Cash register open, cash in/out, close (*corte de caja*)
- [ ] Sale Cart: Counter POS UI, HTMX cart fragments, pure pricing math
- [ ] Checkout: Split payments (cash/card/transfer), change, atomic stock deduction
- [ ] Tickets: HTML/CSS print-ready receipt & ticket lookup
- [ ] Inventory: Stock movement ledger (kardex) & manual stock adjustments
- [ ] Returns: Partial/full ticket refunds, cash-shift impact, restock
- [ ] Reports: Daily & shift summaries by tender, top products, low stock
- [ ] E2E Verification: Full counter-to-corte integration test suite

## Verification
Run `./mvnw test` across all Flyway migrations and integration tests covering login, PIN switch, open shift, split-payment sale, receipt print view, partial return, shift close variance, and daily reports.
