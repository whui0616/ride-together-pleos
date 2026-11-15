#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();
my ($bin, $in, $y0, $outWebp, $tmp, $preview) = @ARGV;
die "usage\n" unless defined $tmp;
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };
sub run { system(@_) == 0 or die "failed: @_\n" }
my ($webp, $pam) = ("$tmp/bg_crop.webp", "$tmp/bg_crop.pam");
run("$bin/cwebp.exe", '-quiet', '-lossless', '-exact', $wp->($in), '-o', $wp->($webp));
run("$bin/dwebp.exe", '-quiet', '-pam', $wp->($webp), '-o', $wp->($pam));
my $d = do { open my $h, '<:raw', $pam or die; local $/; <$h> };
$d =~ /^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+(\d+)\s+MAXVAL\s+\d+\s+TUPLTYPE\s+\S+\s+ENDHDR\n/s or die "pam";
my ($w, $h, $dep) = ($1, $2, $3); my $px = substr($d, length $&);
die "want 800 wide RGBA" unless $w == 800 && $dep == 4;
my $ch = 450; die "y0 out of range" if $y0 < 0 || $y0 + $ch > $h;
sub png { my ($file, $pw, $ph, $rgba) = @_;
    my $raw = ''; for my $y (0 .. $ph - 1) { $raw .= "\0" . substr($rgba, $y * $pw * 4, $pw * 4) }
    my $chunk = sub { my ($t, $dd) = @_; pack('N', length $dd) . $t . $dd . pack('N', Compress::Zlib::crc32($t . $dd)) };
    open my $o, '>:raw', $file or die; print $o "\x89PNG\r\n\x1a\n" . $chunk->('IHDR', pack('NNCCCCC', $pw, $ph, 8, 6, 0, 0, 0)) . $chunk->('IDAT', Compress::Zlib::compress($raw, 6)) . $chunk->('IEND', ''); close $o; }
my $crop = ''; for my $y ($y0 .. $y0 + $ch - 1) { $crop .= substr($px, $y * $w * 4, $w * 4) }
png($preview, $w, $ch, $crop) if $preview;
my $big = '';
for my $y (0 .. $ch - 1) { my $row = ''; for my $x (0 .. $w - 1) { my $p = substr($crop, ($y * $w + $x) * 4, 4); $row .= $p . $p } $big .= $row . $row }
my $bigPng = "$tmp/bg_crop_2x.png"; png($bigPng, $w * 2, $ch * 2, $big);
run("$bin/cwebp.exe", '-quiet', '-q', 90, '-m', 6, $wp->($bigPng), '-o', $wp->($outWebp));
print "wrote $outWebp (1600x900 from y $y0)\n";
