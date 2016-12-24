SELECT rubygems.name, versions.number, linksets.home, gem_downloads.count
FROM gem_downloads
  INNER JOIN rubygems ON rubygems.id = gem_downloads.rubygem_id
  INNER JOIN versions ON versions.id = gem_downloads.version_id
  INNER JOIN linksets ON linksets.rubygem_id = gem_downloads.rubygem_id
ORDER BY gem_downloads.count DESC
LIMIT 100