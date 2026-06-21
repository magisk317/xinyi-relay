import sys
import json
import os

def main():
    if len(sys.argv) < 2:
        print("Usage: python clear_pnpm_overrides.py <path_to_package.json>")
        sys.exit(1)

    package_json_path = sys.argv[1]
    
    if not os.path.exists(package_json_path):
        print(f"Error: {package_json_path} does not exist.")
        sys.exit(1)

    with open(package_json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # pnpm overrides are usually in the "pnpm" object: "pnpm": { "overrides": { ... } }
    # Sometimes it can be top-level "resolutions" or "overrides" depending on package manager,
    # but for pnpm it is specifically "pnpm": { "overrides": { ... } }
    changed = False

    if "pnpm" in data and isinstance(data["pnpm"], dict):
        if "overrides" in data["pnpm"]:
            print(f"Clearing pnpm overrides in {package_json_path}...")
            data["pnpm"]["overrides"] = {}
            changed = True
            
            # If the pnpm block is now empty (or just has empty overrides), 
            # we don't necessarily need to delete it, but keeping it empty is fine.

    if changed:
        with open(package_json_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("Done.")
    else:
        print("No pnpm overrides found. Skipping.")

if __name__ == "__main__":
    main()
