#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();

my ($bin, $pack, $assets, $pngOut, $tmp, @opt) = @ARGV;
die "usage: eye_colors.pl <libwebp bin> <pack dir> <assets pixel dir> <png out dir> <tmp dir> [--check] [--only-new] [--eyes a,b]\n" unless defined $tmp;
my $check = grep { $_ eq '--check' } @opt;
my $onlyNew = grep { $_ eq '--only-new' } @opt;
my @eyes = (0 .. 13);
for my $i (0 .. $#opt) { @eyes = split /,/, $opt[$i + 1] if $opt[$i] eq '--eyes' }

my %NEW = (
    6  => ['6D4E9E', '바이올렛'],
    7  => ['C06A8C', '핑크'],
    8  => ['A33F3F', '레드'],
    9  => ['3B9C9C', '터콰이즈'],
    10 => ['C9A02E', '골드'],
    11 => ['2A2A2E', '블랙'],
    12 => ['78B2E6', '스카이블루'],
    13 => ['2E9A62', '에메랄드'],
    14 => ['9A8AD0', '라벤더'],
    15 => ['A4A8B2', '실버'],
);
my %PACK = (0 => '71503A', 3 => '477E5F', 4 => '4A7EA5');
my ($W, $H) = (800, 1200);
my ($PX, $PY) = (252, 423);
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };

sub run { my @c = @_; system(@c) == 0 or die "failed: @c\n" }

sub loadPng {
    my ($png) = @_;
    my $webp = "$tmp/eye_colors_load.webp"; my $pam = "$tmp/eye_colors_load.pam";
    run("$bin/cwebp.exe", '-quiet', '-lossless', '-exact', $wp->($png), '-o', $wp->($webp));
    run("$bin/dwebp.exe", '-quiet', '-pam', $wp->($webp), '-o', $wp->($pam));
    my $d = do { open my $h, '<:raw', $pam or die "$pam: $!"; local $/; <$h> };
    $d =~ /^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+(\d+)\s+MAXVAL\s+\d+\s+TUPLTYPE\s+\S+\s+ENDHDR\n/s or die "bad pam $png";
    die "$png: depth $3 (want 4)" unless $3 == 4;
    return ($1, $2, substr($d, length $&));
}

sub savePng {
    my ($file, $w, $h, $rgba) = @_;
    my $raw = '';
    for my $y (0 .. $h - 1) { $raw .= "\0" . substr($rgba, $y * $w * 4, $w * 4) }
    my $chunk = sub { my ($t, $d) = @_; pack('N', length $d) . $t . $d . pack('N', Compress::Zlib::crc32($t . $d)) };
    my $png = "\x89PNG\r\n\x1a\n" . $chunk->('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)) . $chunk->('IDAT', Compress::Zlib::compress($raw, 9)) . $chunk->('IEND', '');
    open my $o, '>:raw', $file or die "$file: $!"; print $o $png; close $o;
}

sub byte {
    my $v = shift;
    my $i = int($v); my $f = $v - $i;
    if ($f > 0.5) { $i++ } elsif ($f == 0.5) { $i++ if $i % 2 }
    return $i < 0 ? 0 : $i > 255 ? 255 : $i;
}

sub tintEye {
    my ($rgba, $hex) = @_;
    my ($cr, $cg, $cb) = map { hex } ($hex =~ /(..)(..)(..)/);
    my $out = $rgba;
    for (my $p = 0; $p < length $rgba; $p += 4) {
        my ($r, $g, $b, $a) = unpack('C4', substr($rgba, $p, 4));
        next if $a == 0;
        my $m = $g > $b ? $g : $b;
        next unless $g > $r + 8 && $b > $r + 8 && $m > 15;
        my $sh = .2 + .95 * $m / 255;
        substr($out, $p, 3) = pack('C3', byte($cr * $sh), byte($cg * $sh), byte($cb * $sh));
    }
    return $out;
}

sub place {
    my ($w, $h, $rgba) = @_;
    my $blank = "\0" x ($W * 4);
    my $canvas = '';
    for my $y (0 .. $H - 1) {
        my $ty = $y - $PY;
        if ($ty < 0 || $ty >= $h) { $canvas .= $blank; next }
        $canvas .= ("\0" x ($PX * 4)) . substr($rgba, $ty * $w * 4, $w * 4) . ("\0" x (($W - $PX - $w) * 4));
    }
    return $canvas;
}

sub toWebp {
    my ($png, $webp) = @_;
    run("$bin/cwebp.exe", '-quiet', '-resize', 400, 600, '-q', 85, '-m', 6, $wp->($png), '-o', $wp->($webp));
}

mkdir $tmp unless -d $tmp;
my ($nConv, $nNew) = (0, 0);
for my $g ('f', 'm') {
    mkdir "$assets/$g/eyes" unless -d "$assets/$g/eyes";
    mkdir "$pngOut/$g" unless -d "$pngOut/$g" || $check;
    for my $n (@eyes) {
        my ($tw, $th, $tpx) = loadPng("$pack/templates/${g}_eyes_$n.png");
        die "${g}_eyes_$n: template ${tw}x$th (want 296 wide)" unless $tw == 296;
        if ($check) {
            for my $c (sort { $a <=> $b } keys %PACK) {
                my $mine = place($tw, $th, tintEye($tpx, $PACK{$c}));
                my ($pw, $ph, $ppx) = loadPng("$pack/png/$g/eyes/eyes_${n}_color_$c.png");
                die "size" unless $pw == $W && $ph == $H;
                my ($n0, $n1, $n2, $big, $max, $opaque) = (0, 0, 0, 0, 0, 0);
                for (my $p = 0; $p < length $ppx; $p += 4) {
                    my @a = unpack('C4', substr($mine, $p, 4)); my @b = unpack('C4', substr($ppx, $p, 4));
                    next if $a[3] == 0 && $b[3] == 0;
                    $opaque++;
                    my $d = 0; for my $k (0 .. 3) { my $e = abs($a[$k] - $b[$k]); $d = $e if $e > $d }
                    $max = $d if $d > $max;
                    if ($d == 0) { $n0++ } elsif ($d <= 1) { $n1++ } elsif ($d <= 2) { $n2++ } else { $big++ }
                }
                printf "check %s eyes_%-2d color_%d: px %5d  exact %5d  ±1 %4d  ±2 %3d  >2 %4d  max %d\n", $g, $n, $c, $opaque, $n0, $n1, $n2, $big, $max;
            }
            next;
        }
        unless ($onlyNew) {
            for my $c (0 .. 5) { toWebp("$pack/png/$g/eyes/eyes_${n}_color_$c.png", "$assets/$g/eyes/eyes_${n}_color_$c.webp"); $nConv++ }
        }
        for my $c (sort { $a <=> $b } keys %NEW) {
            my $png = "$pngOut/$g/eyes_${n}_color_$c.png";
            savePng($png, $W, $H, place($tw, $th, tintEye($tpx, $NEW{$c}[0])));
            toWebp($png, "$assets/$g/eyes/eyes_${n}_color_$c.webp");
            $nNew++;
        }
        print "$g eyes_$n done\n";
    }
}
print "converted $nConv pack PNGs, generated $nNew new-color layers (", scalar(keys %NEW), " colors: ", join(' ', map { "$_=$NEW{$_}[1]" } sort { $a <=> $b } keys %NEW), ")\n" unless $check;
