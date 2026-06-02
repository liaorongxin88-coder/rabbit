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
    layoutCols = 3
    layoutLayers = 1
    remark = "demo"
    requestId = (New-ReqId)
} -Headers $authHeaders

$houseId = [int64]$house.data.id
Write-Host ("houseId=" + $houseId)

$houseHeaders = @{
    Authorization = ("Bearer " + $token)
    "X-House-Id" = [string]$houseId
}

$cages = Invoke-Api -Method "GET" -Path "/api/cages" -Headers $houseHeaders
if ($cages.data.Count -lt 3) {
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

$events1 = Invoke-Api -Method "GET" -Path "/api/events" -Headers $houseHeaders
Write-Host "events(after mating)="
$events1.data | ConvertTo-Json -Depth 20 | Write-Host

Invoke-Api -Method "POST" -Path "/api/rabbits/replacement" -Body @{
    rabbitIds = @($femaleId)
    forceExitBatch = $true
    targetCageId = $cage3
    requestId = (New-ReqId)
} -Headers $houseHeaders | Out-Null

$events2 = Invoke-Api -Method "GET" -Path "/api/events" -Headers $houseHeaders
Write-Host "events(after replacement)="
$events2.data | ConvertTo-Json -Depth 20 | Write-Host

Write-Host "done"

