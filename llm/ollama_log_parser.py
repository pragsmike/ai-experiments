#!/usr/bin/env python3
import sys
import re

def parse_ollama_log():
    """
    Parses Ollama log from stdin to extract model loading information
    based on the provided comprehensive log format.
    """
    loaded_models_data = []
    
    # State variables to hold information for the current model being processed
    current_model_name = None
    current_explicit_size_label = None # From "general.size_label"
    current_size_label_from_print_info = None # From "print_info: model type"

    for line in sys.stdin:
        line = line.strip()

        # --- Model Name Extraction ---
        # Pattern 1a: Model name from "llama_model_loader" (most preferred)
        # Example: llama_model_loader: - kv 2: general.name str = Mixtral-8x22B-Instruct-v0.1
        name_match_kv = re.search(r"llama_model_loader: - kv \d+: general\.name str = (.*)", line)
        if name_match_kv:
            name_value = name_match_kv.group(1).strip()
            if name_value:  # Only update if the extracted name is not empty
                current_model_name = name_value
        
        # Pattern 1b: Model name from "print_info" (fallback if llama_model_loader didn't provide one)
        # Example: print_info: general.name = Mixtral-8x22B-Instruct-v0.1
        name_match_print_info = re.search(r"print_info: general\.name\s*=\s*(.*)", line)
        if name_match_print_info:
            name_value = name_match_print_info.group(1).strip()
            if name_value: # Only update if the extracted name is not empty
                # Update only if current_model_name is not already set by a more preferred source (llama_model_loader)
                # or if the preferred source yielded an empty/None name.
                if not current_model_name: 
                    current_model_name = name_value
        
        # --- Size Label Extraction ---
        # Pattern 2a: Explicit size label from "llama_model_loader" (most preferred)
        # Example: llama_model_loader: - kv 6: general.size_label str = 70B
        size_label_match_kv = re.search(r"llama_model_loader: - kv \d+: general\.size_label str = (.*)", line)
        if size_label_match_kv:
            size_value = size_label_match_kv.group(1).strip()
            if size_value: # Only update if the extracted size label is not empty
                current_explicit_size_label = size_value

        # Pattern 2b: Size label from "print_info: model type" (good fallback)
        # Example: print_info: model type       = 8x22B
        size_label_match_print_info = re.search(r"print_info: model type\s*=\s*(.*)", line)
        if size_label_match_print_info:
            size_value = size_label_match_print_info.group(1).strip()
            # Avoid placeholder values like "?B"
            if size_value and size_value != "?B":
                current_size_label_from_print_info = size_value
        
        # --- GPU Offload and Data Recording ---
        # Pattern 3: GPU offload information. This line triggers data recording for the current model context.
        # Example: load_tensors: offloaded 5/57 layers to GPU
        gpu_match = re.search(r"load_tensors: offloaded (\d+)/(\d+) layers to GPU", line)
        if gpu_match:
            # Only record if we have gathered a model name for context
            if current_model_name:
                offloaded_layers = gpu_match.group(1)
                total_layers = gpu_match.group(2)
                gpu_offload_info = f"{offloaded_layers}/{total_layers}"

                # Determine the final size label based on priority
                final_size_label = "N/A" # Default if no size info found
                if current_explicit_size_label:
                    final_size_label = current_explicit_size_label
                elif current_size_label_from_print_info:
                    final_size_label = current_size_label_from_print_info
                else:
                    # Last resort: try to infer size from the model name
                    # Regex for common size patterns like 7B, 8x22B, 70B, 32B, Q4_K_M etc.
                    size_infer_match = re.search(r'\b(\d+(?:x\d+)?[BbKkMmGg][\w-]*)\b', current_model_name, re.IGNORECASE)
                    if size_infer_match:
                        final_size_label = size_infer_match.group(1)
                
                loaded_models_data.append({
                    "name": current_model_name,
                    "size_label": final_size_label,
                    "gpu_offload": gpu_offload_info,
                })
                
                # Reset state variables for the next model loading event
                current_model_name = None 
                current_explicit_size_label = None
                current_size_label_from_print_info = None
            # else:
                # This case means a "load_tensors" line appeared without any preceding
                # model name information being captured since the last model was recorded.
                # This is unlikely in well-formed logs for a model load but we'll ignore it.
                pass
                
    return loaded_models_data

def print_org_table(models_data):
    """
    Prints the extracted model data in Org-mode table format.
    Adjusts column widths dynamically.
    """
    if not models_data:
        print("No model loading events found matching the criteria.")
        return

    # Determine maximum column widths for nice formatting
    # Include header text in width calculation
    max_name_len = len("Model Name")
    if models_data:
        max_name_len = max(max_name_len, max(len(m["name"]) for m in models_data))

    max_size_len = len("Size Label")
    if models_data:
        max_size_len = max(max_size_len, max(len(m["size_label"]) for m in models_data))
    
    max_gpu_len = len("GPU Offload")
    if models_data:
        max_gpu_len = max(max_gpu_len, max(len(m["gpu_offload"]) for m in models_data))

    # Print table header
    header = f"| {'Model Name':<{max_name_len}} | {'Size Label':<{max_size_len}} | {'GPU Offload':<{max_gpu_len}} |"
    print(header)
    
    # Print separator line
    separator = f"|{'-' * (max_name_len + 2)}+{'-' * (max_size_len + 2)}+{'-' * (max_gpu_len + 2)}|"
    print(separator)

    # Print data rows
    for model in models_data:
        row = f"| {model['name']:<{max_name_len}} | {model['size_label']:<{max_size_len}} | {model['gpu_offload']:<{max_gpu_len}} |"
        print(row)

if __name__ == "__main__":
    parsed_data = parse_ollama_log()
    print_org_table(parsed_data)
