import importlib.metadata as md
import graphrag, torch, platform, ctypes.util

print("GraphRAG version:", md.version("graphrag"))
print("Torch version   :", torch.__version__)
print("Python version  :", platform.python_version())
print("libstdc++ found :", ctypes.util.find_library("stdc++"))
