#!/usr/bin/env python3
"""
Script to process user_data.sh template file for direct execution.

This script replaces Terraform template variables with actual values:
- ${eni_mgmt} -> eth0
- ${eni_ingress} -> eth1
- ${eni_egress} -> eth2
- ${create_customers_file} -> content of create_customers.sh
- $$ -> $ (converts Terraform escapes to bash syntax)
"""

import sys
import re
import os
from pathlib import Path

def process_user_data_template(template_file, create_customers_file, output_file):
    """
    Process the user_data.sh template file for direct execution.
    
    Args:
        template_file: Path to user_data.sh template
        create_customers_file: Path to create_customers.sh
        output_file: Path to write the processed script
    """
    # Get the directory of this script
    script_dir = Path(__file__).parent
    
    # Read user_data.sh template
    template_path = script_dir / template_file
    if not template_path.exists():
        print(f"Error: Template file not found: {template_path}", file=sys.stderr)
        sys.exit(1)
    
    with open(template_path, 'r') as f:
        user_data = f.read()
    
    # Read create_customers.sh
    create_customers_path = script_dir / create_customers_file
    if not create_customers_path.exists():
        print(f"Error: create_customers.sh not found: {create_customers_path}", file=sys.stderr)
        sys.exit(1)
    
    with open(create_customers_path, 'r') as f:
        create_customers = f.read()
    
    # Replace Terraform template variables
    user_data = user_data.replace('${eni_mgmt}', 'eth0')
    user_data = user_data.replace('${eni_ingress}', 'eth1')
    user_data = user_data.replace('${eni_egress}', 'eth2')
    user_data = user_data.replace('${create_customers_file}', create_customers)
    
    # Replace Terraform escapes: $$ becomes $ (for running directly, not through Terraform)
    # We need to replace:
    # - $$( -> $(
    # - $${ -> ${
    # - $$VAR (where VAR is a variable name) -> $VAR
    user_data = re.sub(r'\$\$\(', '$(', user_data)  # $$( -> $(
    user_data = re.sub(r'\$\$\{', '${', user_data)  # $${ -> ${
    user_data = re.sub(r'\$\$([A-Za-z_][A-Za-z0-9_]*)', r'$\1', user_data)  # $$VAR -> $VAR
    
    # Write processed file
    output_path = script_dir / output_file if not os.path.isabs(output_file) else Path(output_file)
    with open(output_path, 'w') as f:
        f.write(user_data)
    
    # Make it executable
    os.chmod(output_path, 0o755)
    
    print(f"Processed script created successfully: {output_path}")
    return output_path


if __name__ == '__main__':
    if len(sys.argv) > 1:
        output_file = sys.argv[1]
    else:
        output_file = 'user_data_processed.sh'
    
    process_user_data_template(
        template_file='user_data.sh',
        create_customers_file='create_customers.sh',
        output_file=output_file
    )
