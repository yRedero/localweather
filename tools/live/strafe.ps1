param([int]$ProcessId = 6020)
# Strafe left, right, left in the live game while taking F2 frames (see mcdrive.ps1).
$s = @('wait:300', 'down:A'); 1..12 | % { $s += 'tap:F2', 'wait:90' }
$s += 'up:A', 'down:D'; 1..24 | % { $s += 'tap:F2', 'wait:90' }
$s += 'up:D', 'down:A'; 1..12 | % { $s += 'tap:F2', 'wait:90' }
$s += 'up:A'
& "$PSScriptRoot\mcdrive.ps1" -ProcessId $ProcessId -Steps $s
