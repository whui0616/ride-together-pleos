#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();
my ($bin, $v3, $tmp, $out, $g, $part, $bandY, $red, $red2, $cy0, $cy1, $mag, $cols) = @ARGV;
die "usage\n" unless defined $red;
$red2 = undef if defined $red2 && $red2 eq '-';
$mag //= 1; $cols //= 4;
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };
sub run { system(@_) == 0 or die "failed: @_\n" }
sub load {
    my ($png) = @_;
    my ($lw, $pam) = ("$tmp/bs_in.webp", "$tmp/bs_in.pam");
    run("$bin/cwebp.exe", '-quiet', '-lossless', '-exact', $wp->($png), '-o', $wp->($lw));
    run("$bin/dwebp.exe", '-quiet', '-pam', $wp->($lw), '-o', $wp->($pam));
    open my $fh, '<:raw', $pam or die "$pam: $!"; local $/; my $d = <$fh>; close $fh;
    $d =~ s/^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+4\s+MAXVAL\s+255\s+TUPLTYPE\s+RGB_ALPHA\s+ENDHDR\s//s or die "bad pam header: $png";
    return ($1, $2, $d);
}
sub chunk { my ($t, $d) = @_; pack('N', length $d) . $t . $d . pack('N', Compress::Zlib::crc32($t . $d)) }
sub savePng {
    my ($path, $w, $h, $d) = @_;
    my $raw = ''; $raw .= "\0" . substr($d, $_ * $w * 4, $w * 4) for 0 .. $h - 1;
    open my $fh, '>:raw', $path or die "$path: $!";
    print $fh "\x89PNG\r\n\x1a\n", chunk('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)), chunk('IDAT', Compress::Zlib::compress($raw, 6)), chunk('IEND', '');
    close $fh;
}
my @tiles;
my ($bw, $bh);
for my $n (0 .. 19) {
    my ($w, $h, $d) = load(sprintf('%s/templates/%s_%s_%02d.png', $v3, $g, $part, $n));
    ($bw, $bh) = ($w, $h); push @tiles, $d;
}
$cy0 //= $bandY; $cy1 //= $bandY + $bh;
my ($r0, $r1) = ($cy0 - $bandY, $cy1 - $bandY); $r0 = 0 if $r0 < 0; $r1 = $bh if $r1 > $bh;
my ($ruler, $gap) = (28, 10);
my ($tw, $th) = (($ruler + $bw) * $mag, ($r1 - $r0) * $mag);
my $rows = int((20 + $cols - 1) / $cols);
my $GW = $cols * ($tw + $gap) + $gap; my $GH = $rows * ($th + $gap) + $gap;
my $img = pack('C4', 255, 255, 255, 255) x ($GW * $GH);
sub px { my ($x, $y, $r, $g_, $b) = @_; return if $x < 0 || $y < 0 || $x >= $GW || $y >= $GH; substr($img, ($y * $GW + $x) * 4, 4) = pack('C4', $r, $g_, $b, 255) }
sub blk { my ($x, $y, @c) = @_; for my $dy (0 .. $mag - 1) { for my $dx (0 .. $mag - 1) { px($x * $mag + $dx, $y * $mag + $dy, @c) } } }
for my $i (0 .. 19) {
    my ($c, $r) = ($i % $cols, int($i / $cols));
    my ($ox, $oy) = ($gap + $c * ($tw + $gap), $gap + $r * ($th + $gap));
    for my $y ($r0 .. $r1 - 1) {
        my $cy = $bandY + $y; my $ty = $y - $r0;
        my $line = $cy % 50 == 0 ? 150 : $cy % 10 == 0 ? 215 : 0;
        for my $x (0 .. $ruler + $bw - 1) {
            my $v = $x < $ruler ? 236 : 246;
            $v = $line if $line;
            if ($cy % 10 == 0 && $x < ($cy % 50 == 0 ? 20 : 8)) { $v = 40 }
            for my $dy (0 .. $mag - 1) { for my $dx (0 .. $mag - 1) { px($ox + $x * $mag + $dx, $oy + $ty * $mag + $dy, $v, $v, $v) } }
        }
        for my $x (0 .. $bw - 1) {
            my ($r_, $g_, $b_, $a) = unpack('C4', substr($tiles[$i], (($y * $bw) + $x) * 4, 4)); next unless $a;
            my $t = $a / 255; my $bg = $line || 246;
            my @cc = (int($r_ * $t + $bg * (1 - $t) + .5), int($g_ * $t + $bg * (1 - $t) + .5), int($b_ * $t + $bg * (1 - $t) + .5));
            for my $dy (0 .. $mag - 1) { for my $dx (0 .. $mag - 1) { px($ox + ($ruler + $x) * $mag + $dx, $oy + $ty * $mag + $dy, @cc) } }
        }
    }
    for my $ry ($red, $red2) {
        next unless defined $ry; my $y = $ry - $bandY - $r0; next if $y < 0 || $y >= $r1 - $r0;
        my @col = $ry == $red ? (230, 30, 30) : (30, 80, 230);
        for my $x (0 .. $ruler + $bw - 1) { next if $x >= $ruler && ($x - $ruler) % 3; for my $dx (0 .. $mag - 1) { px($ox + $x * $mag + $dx, $oy + $y * $mag, @col) } }
    }
}
savePng($out, $GW, $GH, $img);
print "$out ${GW}x$GH (band ${bw}x$bh at y=$bandY, rows $cy0..$cy1, x$mag, $cols cols)\n";
