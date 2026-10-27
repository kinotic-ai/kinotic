# The shared development server — one per platform zone (see main.tf).
environment = "dev"
project     = "kinotic"
location    = "centralus"

# Summit 42 beside the servers, whose sign-in and sign-up run through the management server
summit42_enabled = true
