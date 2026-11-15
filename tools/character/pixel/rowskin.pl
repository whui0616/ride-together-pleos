#!/usr/bin/perl
use strict; use warnings;
my ($bin, $tmp, $png, $by, $bx, $y0, $y1, $x0, $x1) = @ARGV;
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };
sub run { system(@_) == 0 or die "failed: @_\n" }
my ($lw, $pam) = ("$tmp/rs_in.webp", "$tmp/rs_in.pam");
run("$bin/cwebp.exe", '-quiet', '-lossless', '-exact', $wp->($png), '-o', $wp->($lw));
run("$bin/dwebp.exe", '-quiet', '-pam', $wp->($lw), '-o', $wp->($pam));
open my $fh, '<:raw', $pam or die; local $/; my $d = <$fh>; close $fh;
$d =~ s/^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+4\s+MAXVAL\s+255\s+TUPLTYPE\s+RGB_ALPHA\s+ENDHDR\s//s or die "bad pam";
my ($w, $h) = ($1, $2);
sub isSkin {
    my ($r, $g, $b) = @_;
    return 0 unless $r >= 150 && $g >= 100 && $b >= 60;
    my $rb = $r - $b; return 0 unless $rb >= 46 && $rb <= 95;
    my $q = ($g - $b) / $rb; return 0 unless $q >= 0.36 && $q <= 0.50;
    my $sat = $rb / $r; return 0 unless $sat >= 0.22 && $sat <= 0.45;
    return 0 if $r > 245 && $g > 235 && $b > 225;
    1;
}
for my $cy ($y0 .. $y1 - 1) {
    my $y = $cy - $by; next if $y < 0 || $y >= $h;
    my ($op, $sk, @sum) = (0, 0, 0, 0, 0); my @runs; my $prev = 0;
    for my $cx ($x0 .. $x1 - 1) {
        my $x = $cx - $bx; next if $x < 0 || $x >= $w;
        my ($r, $g, $b, $a) = unpack('C4', substr($d, ($y * $w + $x) * 4, 4)); next if $a < 8;
        $op++; $sum[0] += $r; $sum[1] += $g; $sum[2] += $b; $sk++ if isSkin($r, $g, $b);
    }
    printf "y%4d: opaque %3d skin %3d mean(%3d,%3d,%3d)\n", $cy, $op, $sk, $op ? map { int($_ / $op) } @sum : (0, 0, 0);
}
