"""Install the bounded return/notice repair using the shared guarded transaction."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'transmog-menu'))
from install_window_queue import main
if __name__=='__main__':main()
