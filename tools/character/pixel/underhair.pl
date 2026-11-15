#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();

my ($bin, $pack, $assets, $pngOut, $tmp) = @ARGV;
die "usage: underhair.pl <libwebp bin> <pack dir> <assets pixel dir> <png out dir> <tmp dir>\n" unless defined $tmp;

my @SKIN = qw(FFE8D8 F4CDAE E3B28E C18B63 955F42 633D2E FFF3EA 4C2E22 3A231B);
my ($W, $H) = (800, 1200);
my ($PX, $PY) = (185, 150);
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };
sub run { system(@_) == 0 or die "failed: @_\n" }

sub loadPng {
    my ($png) = @_;
    my $webp = "$tmp/underhair_load.webp"; my $pam = "$tmp/underhair_load.pam";
    run("$bin/cwebp.exe", '-quiet', '-lossless', '-exact', $wp->($png), '-o', $wp->($webp));
    run("$bin/dwebp.exe", '-quiet', '-pam', $wp->($webp), '-o', $wp->($pam));
    my $d = do { open my $h, '<:raw', $pam or die "$pam: $!"; local $/; <$h> };
    $d =~ /^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+(\d+)\s+MAXVAL\s+\d+\s+TUPLTYPE\s+\S+\s+ENDHDR\n/s or die "bad pam $png";
    die "$png: depth $3" unless $3 == 4;
    return ($1, $2, substr($d, length $&));
}
sub savePng {
    my ($file, $w, $h, $rgba) = @_;
    my $raw = ''; for my $y (0 .. $h - 1) { $raw .= "\0" . substr($rgba, $y * $w * 4, $w * 4) }
    my $chunk = sub { my ($t, $d) = @_; pack('N', length $d) . $t . $d . pack('N', Compress::Zlib::crc32($t . $d)) };
    open my $o, '>:raw', $file or die "$file: $!";
    print $o "\x89PNG\r\n\x1a\n" . $chunk->('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)) . $chunk->('IDAT', Compress::Zlib::compress($raw, 9)) . $chunk->('IEND', '');
    close $o;
}
sub byte { my $v = shift; my $i = int($v); my $f = $v - $i; if ($f > 0.5) { $i++ } elsif ($f == 0.5) { $i++ if $i % 2 } $i < 0 ? 0 : $i > 255 ? 255 : $i }
sub mix { my ($a, $b, $t) = @_; $t = 0 if $t < 0; $t = 1 if $t > 1; $a + ($b - $a) * $t }
sub tintSkin {
    my ($rgba, $hex) = @_;
    my ($cr, $cg, $cb) = map { hex } ($hex =~ /(..)(..)(..)/);
    my $out = $rgba;
    for (my $p = 0; $p < length $rgba; $p += 4) {
        my ($r, $g, $b, $a) = unpack('C4', substr($rgba, $p, 4));
        next if $a == 0;
        next unless $r > $g + 20 && $b > $g + 20 && $r + $b > 100;
        my $sh = .52 + .48 * ($r > $b ? $r : $b) / 255; my $hi = $g / 255 * .35;
        substr($out, $p, 3) = pack('C3', byte(mix($cr * $sh, 255, $hi)), byte(mix($cg * $sh, 244, $hi)), byte(mix($cb * $sh, 233, $hi)));
    }
    $out;
}
sub place {
    my ($w, $h, $rgba) = @_;
    my $blank = "\0" x ($W * 4); my $canvas = '';
    for my $y (0 .. $H - 1) {
        my $ty = $y - $PY;
        if ($ty < 0 || $ty >= $h) { $canvas .= $blank; next }
        $canvas .= ("\0" x ($PX * 4)) . substr($rgba, $ty * $w * 4, $w * 4) . ("\0" x (($W - $PX - $w) * 4));
    }
    $canvas;
}
mkdir $tmp unless -d $tmp;
my $n = 0;
for my $g ('f', 'm') {
    my ($tw, $th, $tpx) = loadPng("$pack/templates/${g}_underhair.png");
    die "${g}_underhair ${tw}x$th (want 430x200)" unless $tw == 430 && $th == 200;
    mkdir "$assets/$g/underhair" unless -d "$assets/$g/underhair";
    mkdir "$pngOut/$g" unless -d "$pngOut/$g";
    for my $s (0 .. $#SKIN) {
        my $png = "$pngOut/$g/underhair_skin_$s.png";
        savePng($png, $W, $H, place($tw, $th, tintSkin($tpx, $SKIN[$s])));
        run("$bin/cwebp.exe", '-quiet', '-resize', 400, 600, '-q', 85, '-m', 6, $wp->($png), '-o', $wp->("$assets/$g/underhair/underhair_skin_$s.webp"));
        $n++;
    }
    print "$g underhair done\n";
}
print "made $n underhair layers\n";
