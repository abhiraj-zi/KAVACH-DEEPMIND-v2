# Tools & Config 
# This file is owned by Backend Engineer

# Configure at kickoff (first 5 min)
EMERGENCY_CONTACTS = {
    "name": "Teammate SOS",
    "number": "+1234567890" # Replace with real teammate number
}

DEMO_LOCATION = {
    "lat": 12.9716,
    "lng": 77.5946, # Bangalore
    "label": "Demo Venue"
}

def dispatch_action_agent(task):
    """
    Called by the Orchestrator to delegate a task to the Action Agent.
    Returns: { "status": "done"|"failed", "detail": "..." }
    """
    pass

def dispatch_comms_agent(task):
    """
    Called by the Orchestrator to delegate a task to the Comms Agent.
    Returns: { "status": "done"|"failed", "detail": "..." }
    """
    pass
