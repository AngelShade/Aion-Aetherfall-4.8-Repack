"""The single supported Playerbots owner configuration path; no runtime fallback."""
from pathlib import Path
CURRENT='config/playerbots/playerbots.properties'
LEGACY='config/main/playerbots.properties'
def active(server):
    server=Path(server)
    return server/CURRENT
