"""Local operator-only triage. Never expose this program through HTTP."""
import argparse
import json
import os
from pathlib import Path
from server import Store

parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["list", "show", "reviewed", "delete", "purge"])
parser.add_argument("report_id", nargs="?")
args = parser.parse_args()
path = Path(os.environ.get("VOXKIN_REPORT_DIR", "/var/lib/voxkin-reports")) / "reports.sqlite3"
if not path.is_file():
    parser.error("report database does not exist")
store = Store(path)
if args.action in {"show", "reviewed", "delete"} and not args.report_id:
    parser.error("report_id required")
with store.lock, store.db:
    if args.action == "list":
        for row in store.db.execute("SELECT id,created,state FROM reports ORDER BY created DESC LIMIT 100"):
            print(json.dumps({"reportId": row[0], "receivedUnix": row[1], "state": row[2]}))
    elif args.action == "show":
        row = store.db.execute("SELECT payload FROM reports WHERE id=?", (args.report_id,)).fetchone()
        if row:
            # JSON escaping prevents terminal-control injection by submitted content.
            print(json.dumps(json.loads(row[0]), ensure_ascii=True))
        else:
            parser.exit(1, "Report not found (possibly expired).\n")
    elif args.action == "reviewed":
        store.db.execute("UPDATE reports SET state='reviewed' WHERE id=?", (args.report_id,))
    elif args.action == "delete":
        store.db.execute("DELETE FROM reports WHERE id=?", (args.report_id,))
# Store construction already purges expired reports before any command.
