import sys

with open('./app/google-services.json', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "io.github.magisk317.relay" in line:
        lines[i] = line.replace("io.github.magisk317.relay", "io.github.magisk317.xinyi.relay")
        break

with open('./app/google-services.json', 'w') as f:
    f.writelines(lines)
