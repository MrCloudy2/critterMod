#!/usr/bin/env python3
"""Generate src/main/resources/assets/crittermod/safari_areas.txt.

The Safari's biomes are not convex regions, so classifying a position by nearest
biome centre is wrong for a few percent of the map. SkyHanni resolves it by walking
its island path graph: find the graph node nearest the player, then search along the
edges for the nearest node tagged with an area name.

That search does not depend on the player, so it can be done once, offline. This
script runs a multi-source Dijkstra from every area-tagged node at once — which gives
each node its graph-nearest area in a single pass — and writes one `x y z biome` row
per node. At runtime the mod only needs a nearest-node lookup, reproducing SkyHanni's
answer without the graph or SkyHanni itself.

Input is SkyHanni's downloaded repo copy of the island graph:
    config/skyhanni/repo/constants/island_graphs/SAFARI.json

Usage:
    python3 tools/generate_safari_areas.py [path/to/SAFARI.json]
"""

import collections
import heapq
import json
import sys
from pathlib import Path

DEFAULT_INPUT = Path.home() / (
    ".local/share/atlauncher/instances/SkyBlockEnhancedModernEdition"
    "/config/skyhanni/repo/constants/island_graphs/SAFARI.json"
)
OUTPUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/crittermod/safari_areas.txt"

# Index 0 is reserved for "no_area" and anything unresolved. Must match
# SafariAreaMap.fromIndex on the Java side.
AREAS = ["Forest Biome", "Cavern Biome", "Icy Biome", "Haunted Biome"]


def main() -> int:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_INPUT
    if not source.is_file():
        print(f"error: no graph at {source}", file=sys.stderr)
        return 1

    graph = json.loads(source.read_text())
    positions = {k: tuple(float(c) for c in v["Position"].split(":")) for k, v in graph.items()}

    area_nodes = {
        k: v["Name"]
        for k, v in graph.items()
        if "area" in (v.get("Tags") or []) and v.get("Name")
    }

    # The graph stores each edge once; area search treats it as undirected.
    adjacency = collections.defaultdict(list)
    for k, v in graph.items():
        for neighbour, weight in (v.get("Neighbours") or {}).items():
            adjacency[k].append((neighbour, float(weight)))
            adjacency[neighbour].append((k, float(weight)))

    best = {k: (float("inf"), None) for k in graph}
    queue = []
    for node, name in area_nodes.items():
        best[node] = (0.0, name)
        heapq.heappush(queue, (0.0, node, name))

    while queue:
        distance, node, name = heapq.heappop(queue)
        if distance > best[node][0] + 1e-9:
            continue
        for neighbour, weight in adjacency[node]:
            candidate = distance + weight
            if candidate < best[neighbour][0] - 1e-9:
                best[neighbour] = (candidate, name)
                heapq.heappush(queue, (candidate, neighbour, name))

    index = {name: i + 1 for i, name in enumerate(AREAS)}
    rows = []
    for node in graph:
        x, y, z = positions[node]
        rows.append(f"{int(x)} {int(y)} {int(z)} {index.get(best[node][1], 0)}")

    OUTPUT.write_text("\n".join(rows) + "\n")

    counts = collections.Counter(v[1] for v in best.values())
    unreached = sum(1 for v in best.values() if v[1] is None)
    print(f"{source.name}: {len(graph)} nodes, {len(area_nodes)} area tags")
    for name, count in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"  {name or 'unreachable':<14} {count}")
    if unreached:
        print(f"warning: {unreached} node(s) reached no area tag", file=sys.stderr)
    print(f"wrote {OUTPUT.relative_to(Path.cwd()) if OUTPUT.is_relative_to(Path.cwd()) else OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
