# 集成收口：前端三道门禁（tsc/build/lint），不改依赖不 install
Set-Location c:\Users\17962\Desktop\Big_Java_Project\AIBishe\frontend
Write-Host '=== tsc --noEmit ==='
npx tsc --noEmit
Write-Host "tsc exit=$LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { exit 1 }
Write-Host '=== npm run build ==='
npm run build
Write-Host "build exit=$LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { exit 1 }
Write-Host '=== npm run lint ==='
npm run lint
Write-Host "lint exit=$LASTEXITCODE"
exit $LASTEXITCODE
