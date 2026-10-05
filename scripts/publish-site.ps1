<#
.SYNOPSIS
    Publishes site/ (privacy policy, account deletion) to the public gratitude-garden-site
    repo, which GitHub Pages serves.

.DESCRIPTION
    This repo is private, so the pages live in their own public repo. site/ here is the
    source: the script copies the committed site/ over the public repo's main branch, commits
    the difference as "Publish site/ from Gratitude-Garden <commit>", and pushes it.

    It refuses to run when the public repo has a commit this script didn't make, e.g. an edit
    in GitHub's web editor, because publishing would silently throw that edit away. Bring the
    edit into site/ here, commit it, then re-run with -RemoteEditsMerged.

    The play-store URLs in util/Links.kt and Play Console point at these pages.

.EXAMPLE
    .\scripts\publish-site.ps1
    Publish the committed site/.

.EXAMPLE
    .\scripts\publish-site.ps1 -RemoteEditsMerged
    Publish after copying an edit made on GitHub into site/.
#>
param(
    [switch]$RemoteEditsMerged
)

$ErrorActionPreference = 'Stop'
$PublishPrefix = 'Publish site/ from Gratitude-Garden'
$RemoteUrl = 'https://github.com/braedons-cse/gratitude-garden-site.git'

function Invoke-Git {
    & git @args
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed" }
}

$root = (& git rev-parse --show-toplevel).Trim()
Set-Location $root

# site/ must be committed, so what's published is a commit anyone can find again.
if (& git status --porcelain -- site) {
    throw 'site/ has uncommitted changes. Commit them first.'
}

if (-not (& git remote | Where-Object { $_ -eq 'site' })) {
    Invoke-Git remote add site $RemoteUrl
}
Invoke-Git fetch -q site main

$head = (& git log -1 --format=%s site/main).Trim()
if (-not $head.StartsWith($PublishPrefix) -and -not $RemoteEditsMerged) {
    Write-Host "The public repo's latest commit wasn't made by this script:" -ForegroundColor Yellow
    & git log -5 --format='  %h %an %ad  %s' --date=short site/main
    Write-Host "Copy that change into site/ here, commit it, then run again with -RemoteEditsMerged."
    exit 1
}

$source = (& git rev-parse --short HEAD).Trim()
$work = Join-Path ([IO.Path]::GetTempPath()) ("gg-site-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
Invoke-Git worktree add -q --detach $work site/main
try {
    # Mirror site/: anything deleted here is deleted there too.
    Get-ChildItem -Force $work | Where-Object { $_.Name -ne '.git' } | Remove-Item -Recurse -Force
    Get-ChildItem -Force (Join-Path $root 'site') | Copy-Item -Destination $work -Recurse -Force

    Invoke-Git -C $work add -A
    & git -C $work diff --cached --quiet
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'The public site already matches site/. Nothing to publish.'
    } else {
        & git -C $work diff --cached --stat
        Invoke-Git -C $work commit -q -m "$PublishPrefix $source"
        Invoke-Git -C $work push -q site HEAD:main
        Write-Host "Published site/ from $source. GitHub Pages updates within a minute or two:"
        Write-Host '  https://braedons-cse.github.io/gratitude-garden-site/'
    }
} finally {
    Invoke-Git worktree remove --force $work
}
