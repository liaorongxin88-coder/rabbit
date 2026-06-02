param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = ("demo_" + [Guid]::NewGuid().ToString("N").Substring(0, 8)),
    [string]$Password = "123456"
)

function New-ReqId {
    return [Guid]::NewGuid().ToString("N")
}

function NowMs {
    return [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body,
        [hashtable]$Headers
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $json = $null
    if ($Body -ne $null) {
        $json = ($Body | ConvertTo-Json -Depth 20)
    }
    $resp = $null
    if ($json -ne $null) {
        $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -Body $json -ContentType "application/json; charset=utf-8"
    } else {
        $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers
    }
    if ($resp -ne $null -and $resp.PSObject.Properties.Name -contains "code") {
        if ([int]$resp.code -ne 0) {
            throw ("api error " + $Path + " code=" + $resp.code + " msg=" + $resp.message)
        }
    }
    return $resp
}

Write-Host ("BaseUrl=" + $BaseUrl)
Write-Host ("Username=" + $Username)

try {
    Invoke-Api -Method "POST" -Path "/api/auth/register" -Body @{
        username = $Username
        password = $Password
        requestId = (New-ReqId)
    } -Headers @{} | Out-Null
} catch {
}

$login = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{
    username = $Username
    password = $Password
} -Headers @{}

$token = $login.data.token
if ([string]::IsNullOrEmpty($token)) {
    throw "login token missing"
}

$authHeaders = @{
    Authorization = ("Bearer " + $token)
}

$house = Invoke-Api -Method "POST" -Path "/api/houses" -Body @{
    name = ("演示兔舍_" + [Guid]::NewGuid().ToString("N").Substring(0, 6))
    layoutRows = 1
    layoutCols = 6
    layoutLayers = 1
    remark = "demo full flow"
    requestId = (New-ReqId)
} -Headers $authHeaders

$houseId = [int64]$house.data.id
Write-Host ("houseId=" + $houseId)

$houseHeaders = @{
    Authorization = ("Bearer " + $token)
    "X-House-Id" = [string]$houseId
}

$cages = Invoke-Api -Method "GET" -Path "/api/cages" -Headers $houseHeaders
if ($cages.data.Count -lt 6) {
    throw "cages not enough"
}
$cage1 = [int64]$cages.data[0].id
$cage2 = [int64]$cages.data[1].id
$cage3 = [int64]$cages.data[2].id

Invoke-Api -Method "PUT" -Path "/api/settings" -Body @{
    aphrodisiacDays = 0
    palpationDays = 0
    prepartumDays = 0
    weaningDays = 0
    postpartumDays = 0
    saleDays = 0
    replacementDays = 0
    remark = "demo: all 0 days"
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

$female = Invoke-Api -Method "POST" -Path "/api/rabbits" -Body @{
    cageId = $cage1
    type = "母兔"
    gender = "F"
    breed = "demo"
    arrivalMethod = "demo"
    arrivalDate = (NowMs)
    weight = 3.2
    requestId = (New-ReqId)
} -Headers $houseHeaders
$femaleId = [int64]$female.data.id

$male = Invoke-Api -Method "POST" -Path "/api/rabbits" -Body @{
    cageId = $cage2
    type = "公兔"
    gender = "M"
    breed = "demo"
    arrivalMethod = "demo"
    arrivalDate = (NowMs)
    weight = 3.6
    requestId = (New-ReqId)
} -Headers $houseHeaders
$maleId = [int64]$male.data.id

$batch = Invoke-Api -Method "POST" -Path "/api/batches" -Body @{
    batchCode = ("B" + [Guid]::NewGuid().ToString("N").Substring(0, 6))
    femaleRabbitIds = @($femaleId)
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $houseHeaders
$batchId = [int64]$batch.data.id
Write-Host ("batchId=" + $batchId)

Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/mating") -Body @{
    femaleRabbitId = $femaleId
    maleRabbitId = $maleId
    matingDate = (NowMs)
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/pregnancy-check") -Body @{
    rabbitId = $femaleId
    checkDate = (NowMs)
    result = "怀孕"
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/prepartum/finish") -Body @{
    rabbitId = $femaleId
    actionDate = (NowMs)
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/parturition") -Body @{
    rabbitId = $femaleId
    birthDate = (NowMs)
    totalKits = 8
    liveKits = 7
    failed = $false
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/weaning") -Body @{
    rabbitId = $femaleId
    weaningDate = (NowMs)
    weaningCount = 6
    maleCount = 3
    femaleCount = 3
    avgWeight = 1.2
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

$kitLinks = Invoke-Api -Method "GET" -Path ("/api/batches/" + $batchId + "/batch-rabbits?role=fattening&active=true") -Headers $houseHeaders
$kidIds = @()
foreach ($br in $kitLinks.data) {
    if ($br -ne $null -and $br.PSObject.Properties.Name -contains "rabbitId") {
        $kidIds += [int64]$br.rabbitId
    }
}

Write-Host ("kids=" + ($kidIds -join ","))
if ($kidIds.Count -gt 0) {
    Invoke-Api -Method "POST" -Path ("/api/batches/" + $batchId + "/sale") -Body @{
        rabbitIds = $kidIds
        saleDate = (NowMs)
        remark = "demo sale"
        requestId = (New-ReqId)
    } -Headers $houseHeaders | Out-Null
}

if ($kidIds.Count -gt 0) {
    Invoke-Api -Method "POST" -Path "/api/rabbits/replacement" -Body @{
        rabbitIds = @($kidIds[0])
        forceExitBatch = $true
        targetCageId = $cage3
        requestId = (New-ReqId)
    } -Headers $houseHeaders | Out-Null
}

$events = Invoke-Api -Method "GET" -Path "/api/events?onlyUnnotified=true" -Headers $houseHeaders
Write-Host "events(onlyUnnotified)="
$events.data | ConvertTo-Json -Depth 20 | Write-Host

Invoke-Api -Method "POST" -Path "/api/maintenance/events/scan" -Headers $houseHeaders | Out-Null

$logs = Invoke-Api -Method "GET" -Path "/api/event-reminder-logs?limit=50" -Headers $houseHeaders
Write-Host "event-reminder-logs="
$logs.data | ConvertTo-Json -Depth 20 | Write-Host

$bp = Invoke-Api -Method "POST" -Path "/api/maintenance/breeding-performance/recalc" -Headers $houseHeaders
Write-Host "breeding-performance-recalc="
$bp.data | ConvertTo-Json -Depth 20 | Write-Host

Write-Host "done"

