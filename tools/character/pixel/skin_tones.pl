#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();
my ($bin, $v3, $dst, $pngOut, $tmp, @bases) = @ARGV;
die "usage: skin_tones.pl <libwebp bin> <v3 dir> <pixel assets dir> <png out dir> <tmp dir> [--check] [base ...]\n" unless $tmp;
my $check = 0;
@bases = grep { $_ eq '--check' ? ($check = 1, 0)[1] : 1 } @bases;
my ($dwebp, $cwebp) = ("$bin/dwebp.exe", "$bin/cwebp.exe");
my ($W, $H) = (800, 1200);

my %OLD = (0 => 'FFE8D8', 1 => 'F4CDAE', 2 => 'E3B28E', 3 => 'C18B63', 4 => '955F42', 5 => '633D2E');
my %NEW = (
    6 => ['FFF3EA', '스노우'],
    7 => ['4C2E22', '에보니'],
    8 => ['3A231B', '딥 에보니'],
);
my %RECT = (
    outfit => [211, 540, 378, 625],
    face_0 => [185, 150, 430, 429], face_1 => [185, 150, 430, 429], face_2 => [185, 150, 430, 429],
);

unless (@bases) {
    for my $g (qw(f m)) {
        push @bases, map { "$g/faces/face_$_" } 0..2;
        push @bases, map { sprintf("%s/outfits/outfit_%02d", $g, $_) } 0..11;
    }
}

sub run { system(@_) == 0 or die "failed: @_\n" }
sub load {
    my ($png) = @_;
    my ($lw, $pam) = ("$tmp/in.webp", "$tmp/in.pam");
    run($cwebp, '-quiet', '-lossless', '-exact', $png, '-o', $lw);
    run($dwebp, '-quiet', '-pam', $lw, '-o', $pam);
    open my $fh, '<:raw', $pam or die "$pam: $!"; local $/; my $d = <$fh>; close $fh;
    $d =~ s/^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+4\s+MAXVAL\s+255\s+TUPLTYPE\s+RGB_ALPHA\s+ENDHDR\s//s or die "bad pam header: $png";
    return ($1, $2, $d);
}
sub chunk { my ($type, $data) = @_; return pack('N', length $data) . $type . $data . pack('N', Compress::Zlib::crc32($type . $data)) }
sub savePng {
    my ($path, $w, $h, $d) = @_;
    my $raw = '';
    $raw .= "\0" . substr($d, $_ * $w * 4, $w * 4) for 0 .. $h - 1;
    open my $fh, '>:raw', $path or die "$path: $!";
    print $fh "\x89PNG\r\n\x1a\n", chunk('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)), chunk('IDAT', Compress::Zlib::compress($raw, 6)), chunk('IEND', '');
    close $fh;
}
sub mkdirs { my ($p) = @_; my $acc = ''; for my $part (split m{/}, $p) { $acc .= "$part/"; mkdir $acc unless -d $acc } }
sub roundEven { my ($v) = @_; my $f = int($v); my $r = $v - $f; return $r > 0.5 ? $f + 1 : $r < 0.5 ? $f : ($f % 2 ? $f + 1 : $f) }
sub byte { my ($v) = @_; $v = roundEven($v); return $v < 0 ? 0 : $v > 255 ? 255 : $v }
sub mix { my ($a, $b, $t) = @_; $t = $t < 0 ? 0 : $t > 1 ? 1 : $t; return $a + ($b - $a) * $t }

sub tint {
    my ($d, $hex) = @_;
    my @c = map { hex(substr($hex, $_ * 2, 2)) } 0..2;
    my $out = '';
    for (my $o = 0; $o < length $d; $o += 4) {
        my ($r, $g, $b, $a) = unpack('C4', substr($d, $o, 4));
        if ($a != 0 && $r > $g + 20 && $b > $g + 20 && $r + $b > 100) {
            my $sh = .52 + .48 * ($r > $b ? $r : $b) / 255;
            my $hi = $g / 255 * .35;
            ($r, $g, $b) = (byte(mix($c[0] * $sh, 255, $hi)), byte(mix($c[1] * $sh, 244, $hi)), byte(mix($c[2] * $sh, 233, $hi)));
        }
        $out .= pack('C4', $r, $g, $b, $a);
    }
    return $out;
}
sub place {
    my ($d, $w, $h, $x, $y) = @_;
    my $blank = pack('C4', 0, 0, 0, 0);
    my $out = $blank x ($W * $y);
    for my $row (0 .. $h - 1) {
        $out .= $blank x $x;
        $out .= substr($d, $row * $w * 4, $w * 4);
        $out .= $blank x ($W - $x - $w);
    }
    $out .= $blank x ($W * ($H - $y - $h));
    return $out;
}

my ($checked, $bad) = (0, 0);
for my $base (@bases) {
    my ($g, $kind, $name) = $base =~ m{^([fm])/(faces|outfits)/(\w+)$} or die "bad base: $base";
    my $rect = $RECT{ $kind eq 'faces' ? $name : 'outfit' };
    my ($w, $h, $d) = load("$v3/templates/${g}_$name.png");
    die "template $base is ${w}x$h, expected $rect->[2]x$rect->[3]" unless $w == $rect->[2] && $h == $rect->[3];
    my ($dir) = $base =~ m{^(.*)/};
    if ($check) {
        my $mine = place(tint($d, $OLD{1}), $w, $h, $rect->[0], $rect->[1]);
        my (undef, undef, $orig) = load("$v3/png/${base}_skin_1.png");
        my ($n, $small, $big, $maxd, $bigAlpha) = (0, 0, 0, 0, 0);
        for (my $o = 0; $o < length $mine; $o += 4) {
            my @a = unpack('C4', substr($mine, $o, 4)); my @b = unpack('C4', substr($orig, $o, 4));
            next if $a[3] == 0 && $b[3] == 0;
            $n++;
            my $dd = 0; for (0..3) { my $x = abs($a[$_] - $b[$_]); $dd = $x if $x > $dd }
            if ($dd <= 2) { $small++ } else { $big++; $maxd = $dd if $dd > $maxd; $bigAlpha = $b[3] if $b[3] > $bigAlpha }
        }
        printf "%-24s skin_1 재생성 vs 원본: 픽셀 %d, 차이 ≤2: %d, 차이 >2: %d (최대 %d, 그중 알파 최대 %d)\n", $base, $n, $small, $big, $maxd, $bigAlpha;
        $checked++; $bad++ if $bigAlpha > 64;
        next;
    }
    mkdirs("$dst/$dir"); mkdirs("$pngOut/png/$dir");
    for my $id (sort { $a <=> $b } keys %NEW) {
        my ($hex, $label) = @{ $NEW{$id} };
        my $canvas = place(tint($d, $hex), $w, $h, $rect->[0], $rect->[1]);
        my $png = "$pngOut/png/${base}_skin_$id.png";
        savePng($png, $W, $H, $canvas);
        run($cwebp, '-quiet', '-resize', $W / 2, $H / 2, '-q', '85', '-m', '6', $png, '-o', "$dst/${base}_skin_$id.webp");
        print "${base}_skin_$id ($label #$hex)\n";
    }
}
print "check: $checked bases, $bad with differences\n" if $check;
