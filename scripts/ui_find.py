"""Find things on an Android screen from a `uiautomator dump` XML file.

  ui_find.py ui.xml "Start week"     -> "x y" centre of the node whose text or description is exactly that
                                        (case-insensitive), or nothing if it is not on screen
  ui_find.py ui.xml id:expand_button -> same, for the first node whose resource-id contains that text
  ui_find.py ui.xml --texts          -> every visible text, joined by " | "
"""
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
query = sys.argv[2]

if query == "--texts":
    print(" | ".join(t for n in root.iter("node") for t in [n.get("text") or n.get("content-desc")] if t))
    sys.exit()

for node in root.iter("node"):
    if query.startswith("id:"):
        hit = query[3:] in (node.get("resource-id") or "")
    else:
        want = query.lower()
        hit = want in ((node.get("text") or "").lower(), (node.get("content-desc") or "").lower())
    if hit:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
