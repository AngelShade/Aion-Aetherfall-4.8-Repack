"""Hash-guarded roster install using the stopped-server cumulative installer."""
import install_supplies_offline as base

# Reuse the existing transaction without weakening its scope check: supply installer
# is factored below to accept the explicit reviewed scope prefix and result label.
if __name__=='__main__':
 base.main('Account-owned Temporary Bot roster archive','Temporary Bot roster removal')
