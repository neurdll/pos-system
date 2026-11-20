---
name: depenta
description: >-
  Manage project task graphs, prerequisites (depends_on), and semantic links
  (related) via the depenta CLI. Activate when decomposing multi-step work into
  a dependency DAG, picking the next unblocked task, or tracking task progress
  inside a .depenta/ project store.
---

# Depenta AI Workflow Skill

Use `depenta` as your machine-verified task dependency graph so you always work on unblocked tasks in topological order and never lose track of downstream impact.

## Non-Negotiable Rules for AI Agents

1. **Never launch the TUI:** Do NOT run `depenta` with no arguments or `depenta tui`—it enters terminal raw mode and blocks non-interactive shells. Always pass a subcommand.
2. **Never invoke `depenta edit` without flags:** Running `depenta edit <id>` with no flags spawns `$EDITOR` (`vi`/`nano`) and hangs. Always pass `--title`, `--desc`, and/or `--priority`.
3. **Never hand-edit `.depenta/tasks/*.json`:** Always mutate via the CLI so DAG acyclicity, symmetric `related` links, and timestamps stay valid.
4. **Use short hash prefixes:** Every `<id>` accepts any unambiguous lowercase hex prefix (e.g., `a3f` instead of `a3f9c1`).

---

## Efficient Execution Loop

### 1. Inspect or Initialize the Graph
Check `.depenta/` discovery by listing actionable work first:
```bash
depenta ready
```
If `.depenta/` does not exist yet in the repository, initialize it (idempotent):
```bash
depenta init
```
To inspect the full graph in prerequisite-first order:
```bash
depenta list --topo
```

### 2. Decompose Work Into a Dependency DAG
Create prerequisite tasks first so you can immediately wire downstream tasks with `--after`:
```bash
# 1. Create root task (prints: Added <hash> [priority] <title>)
depenta add "Define data model" -p high -d "Pure Task struct and status enum"

# 2. Create downstream task already linked to prerequisite
depenta add "Implement graph engine" -p high --after <root_hash>

# 3. Link or relate existing tasks
depenta link <dependent_id> --after <prereq_id>
depenta link <prereq_id> --before <dependent_id>
depenta link <id_a> --rel <id_b>   # symmetric, non-blocking context link
```
- **When to use `--after` / `--before`:** Hard ordering where task B cannot start until task A is `done` (strictly acyclic DAG).
- **When to use `--rel`:** Related context, shared files, or parallel work that should not block execution (cycles allowed).

### 3. Work the Ready Frontier
`depenta ready` ranks unblocked `todo` tasks by **priority** (highest first) and then **downstream impact** (how many incomplete tasks it transitively unblocks):
```bash
depenta ready                  # Pick top task
depenta show <id>              # Read description, blockers, unlocks, and related tasks
depenta status <id> in_progress
```

### 4. Complete & Advance Without Extra Queries
When a task is finished:
```bash
depenta done <id>
```
`done` immediately prints `Newly unblocked:` tasks so you can transition straight to the next task.

### 5. Recovering From Cycle Errors or Scope Changes
If `depenta link` rejects an edge with `dependency would create a cycle: a -> b -> ... -> a`:
- Inspect the chain with `depenta show <id>`.
- Remove the misplaced dependency with `depenta unlink <id> --after <other>`.
- If the connection is informational rather than blocking, re-link with `--rel`.

---

## CLI Quick Reference

| Command | Purpose | Key Options |
| :--- | :--- | :--- |
| `depenta init` | Initialize `.depenta/tasks/` in CWD | — |
| `depenta add "<title>"` | Create task and optional links | `-p/--priority`, `-d/--desc`, `--after ID`, `--rel ID` *(repeatable)* |
| `depenta link <id>` | Add edges | `--after ID`, `--before ID`, `--rel ID` |
| `depenta unlink <id>` | Remove edges | `--after ID`, `--before ID`, `--rel ID` |
| `depenta ready` | List unblocked `todo` tasks by priority & impact | — |
| `depenta list` | List tasks | `--all` *(default)*, `--ready`, `--blocked`, `--done`, `--topo` |
| `depenta show <id>` | Inspect description & neighborhood | — |
| `depenta status <id> <state>` | Update status | `todo`, `in_progress` (`wip`/`doing`), `done` |
| `depenta done <id>` | Mark `done` and print newly unblocked tasks | — |
| `depenta edit <id>` | Non-interactive metadata update | `-t/--title`, `-d/--desc`, `-p/--priority` |
| `depenta rm <id>` | Delete task and clean up incoming links | — |

**Priority aliases:** `low` (`l`, `p3`), `medium` (`med`, `m`, `p2`), `high` (`h`, `p1`), `critical` (`crit`, `c`, `p0`).
