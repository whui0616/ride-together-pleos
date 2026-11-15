#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();
my ($bin, $src, $dst, $pngOut, $tmp, @opt) = @ARGV;
die "usage: outfits20.pl <libwebp bin> <template dir> <pixel assets dir> <png out dir> <tmp dir> [--preview <prefix>] [--no-write] [--skins ..] [--ids ..] [--g ..] [--faces <dir>] [--probe] [--zoom g:n:x0,y0,x1,y1]\n" unless defined $tmp;
my ($preview, $write, $probe, $faces, @zoom, %o) = (undef, 1, 0, undef);
while (@opt) { my $k = shift @opt; if ($k eq '--preview') { $preview = shift @opt } elsif ($k eq '--no-write') { $write = 0 } elsif ($k eq '--probe') { $probe = 1 } elsif ($k eq '--faces') { $faces = shift @opt } elsif ($k eq '--zoom') { push @zoom, shift @opt } elsif ($k =~ /^--(skins|ids|g)$/) { $o{$1} = [split /,/, shift @opt] } else { die "unknown option $k\n" } }
my @skins = @{ $o{skins} || [0 .. 8] }; my @ids = @{ $o{ids} || [12 .. 19] }; my @gs = @{ $o{g} || [qw(f m)] };
my ($dwebp, $cwebp) = ("$bin/dwebp.exe", "$bin/cwebp.exe");
my ($W, $H) = (800, 1200);
my @RECT = (211, 540, 378, 625);
my @SKIN = qw(FFE8D8 F4CDAE E3B28E C18B63 955F42 633D2E FFF3EA 4C2E22 3A231B);
my ($MIN_BLOB, $PAD) = (400, 10);
my %ZONES = (
    f => { 14 => [[364, 544, 437, 584], [356, 584, 444, 606], [374, 606, 429, 646], [226, 818, 286, 876], [516, 820, 576, 870]] },
    m => {},
);

sub run { system(@_) == 0 or die "failed: @_\n" }
sub wp { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p }
sub load {
    my ($png) = @_;
    my ($lw, $pam) = ("$tmp/in.webp", "$tmp/in.pam");
    run($cwebp, '-quiet', '-lossless', '-exact', wp($png), '-o', wp($lw));
    run($dwebp, '-quiet', '-pam', wp($lw), '-o', wp($pam));
    open my $fh, '<:raw', $pam or die "$pam: $!"; local $/; my $d = <$fh>; close $fh;
    $d =~ s/^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+4\s+MAXVAL\s+255\s+TUPLTYPE\s+RGB_ALPHA\s+ENDHDR\s//s or die "bad pam header: $png";
    return ($1, $2, $d);
}
sub chunk { my ($t, $d) = @_; pack('N', length $d) . $t . $d . pack('N', Compress::Zlib::crc32($t . $d)) }
sub savePng {
    my ($path, $w, $h, $d) = @_; my $raw = '';
    $raw .= "\0" . substr($d, $_ * $w * 4, $w * 4) for 0 .. $h - 1;
    open my $fh, '>:raw', $path or die "$path: $!";
    print $fh "\x89PNG\r\n\x1a\n", chunk('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)), chunk('IDAT', Compress::Zlib::compress($raw, 6)), chunk('IEND', '');
    close $fh;
}
sub mkdirs { my ($p) = @_; my $acc = ''; for my $part (split m{/}, $p) { $acc .= "$part/"; mkdir $acc unless -d $acc } }
sub roundEven { my ($v) = @_; my $f = int($v); my $r = $v - $f; return $r > 0.5 ? $f + 1 : $r < 0.5 ? $f : ($f % 2 ? $f + 1 : $f) }
sub byte { my ($v) = @_; $v = roundEven($v); $v < 0 ? 0 : $v > 255 ? 255 : $v }
sub mix { my ($a, $b, $t) = @_; $t = $t < 0 ? 0 : $t > 1 ? 1 : $t; $a + ($b - $a) * $t }
sub lum { .2126 * $_[0] + .7152 * $_[1] + .0722 * $_[2] }
sub place {
    my ($d, $w, $h, $x, $y) = @_; my $blank = pack('C4', 0, 0, 0, 0);
    my $out = $blank x ($W * $y);
    for my $row (0 .. $h - 1) { $out .= $blank x $x; $out .= substr($d, $row * $w * 4, $w * 4); $out .= $blank x ($W - $x - $w) }
    $out . $blank x ($W * ($H - $y - $h));
}
sub over {
    my ($ref, $src) = @_;
    for (my $o = 0; $o < length $src; $o += 4) {
        my $sa = ord(substr($src, $o + 3, 1)); next if $sa == 0;
        if ($sa == 255) { substr($$ref, $o, 4) = substr($src, $o, 4); next }
        my ($sr, $sg, $sb) = unpack('C3', substr($src, $o, 3)); my ($dr, $dg, $db, $da) = unpack('C4', substr($$ref, $o, 4));
        my $fa = $sa / 255; my $fd = $da / 255 * (1 - $fa); my $oa = $fa + $fd;
        substr($$ref, $o, 4) = pack('C4', int(($sr * $fa + $dr * $fd) / $oa + 0.5), int(($sg * $fa + $dg * $fd) / $oa + 0.5), int(($sb * $fa + $db * $fd) / $oa + 0.5), int($oa * 255 + 0.5));
    }
}
my $LBASE = lum(239, 201, 173);
sub isSkin {
    my ($r, $g, $b) = @_;
    return 0 unless $r >= 150 && $g >= 100 && $b >= 60;
    my $rb = $r - $b; return 0 unless $rb >= 46 && $rb <= 95;
    my $q = ($g - $b) / $rb; return 0 unless $q >= 0.36 && $q <= 0.50;
    my $sat = $rb / $r; return 0 unless $sat >= 0.22 && $sat <= 0.45;
    return 0 if $r > 245 && $g > 235 && $b > 225;
    1;
}
sub isSkinLoose {
    my ($r, $g, $b) = @_;
    return 0 unless $r >= 140 && $r > $g && $g > $b && lum($r, $g, $b) >= 105;
    my $rb = $r - $b; return 0 unless $rb >= 40 && $rb <= 130;
    my $q = ($g - $b) / $rb; return 0 unless $q >= 0.12 && $q <= 0.56;
    my $sat = $rb / $r; return 0 unless $sat >= 0.18 && $sat <= 0.66;
    1;
}
sub blobs {
    my ($ref) = @_; my %c;
    for (my $o = 0; $o < length $$ref; $o += 4) { my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $o, 4)); $c{$o} = 1 if $a >= 8 && isSkin($r, $g, $b) }
    my @out;
    for my $s (sort { $a <=> $b } keys %c) {
        next unless $c{$s}; my @q = ($s); delete $c{$s}; my ($n, $x0, $y0, $x1, $y1) = (0, $W, $H, 0, 0);
        while (@q) { my $o = pop @q; $n++; my $p = $o / 4; my ($x, $y) = ($p % $W, int($p / $W));
            $x0 = $x if $x < $x0; $x1 = $x if $x > $x1; $y0 = $y if $y < $y0; $y1 = $y if $y > $y1;
            for my $d (-4, 4, -$W * 4, $W * 4) { my $t = $o + $d; if ($c{$t}) { delete $c{$t}; push @q, $t } } }
        push @out, [$n, $x0, $y0, $x1 + 1, $y1 + 1];
    }
    [sort { $a->[2] <=> $b->[2] || $a->[1] <=> $b->[1] } @out];
}
sub skinMask {
    my ($ref, $zones) = @_; my (%in, %m, @q);
    for my $z (@$zones) { for my $y ($z->[1] .. $z->[3] - 1) { for my $x ($z->[0] .. $z->[2] - 1) { $in{ ($y * $W + $x) * 4 } = 1 } } }
    for my $o (keys %in) { my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $o, 4)); next if $a < 8; if (isSkin($r, $g, $b)) { $m{$o} = 1; push @q, $o } }
    my $seed = scalar keys %m;
    while (@q) {
        my $o = pop @q;
        for my $d (-4, 4, -$W * 4, $W * 4) {
            my $t = $o + $d; next if $m{$t} || !$in{$t};
            my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $t, 4)); next if $a < 8 || !isSkinLoose($r, $g, $b);
            $m{$t} = 1; push @q, $t;
        }
    }
    my @edge;
    for my $o (keys %m) {
        for my $d (-4, 4, -$W * 4, $W * 4) {
            my $t = $o + $d; next if $m{$t} || !$in{$t};
            my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $t, 4)); next if $a < 8;
            push @edge, $t if $r >= 80 && $r > $g && $g >= $b && $r - $b >= 25 && lum($r, $g, $b) >= 45;
        }
    }
    $m{$_} = 1 for @edge;
    ([sort { $a <=> $b } keys %m], $seed);
}
sub recolor {
    my ($ref, $mask, $hex) = @_; my @c = map { hex(substr($hex, $_ * 2, 2)) } 0 .. 2; my $out = $$ref;
    for my $o (@$mask) {
        my ($r, $g, $b) = unpack('C3', substr($out, $o, 3)); my $s = lum($r, $g, $b) / $LBASE;
        my @v = $s <= 1 ? (map { $_ * $s } @c) : do { my $t = ($s - 1) / 0.25; (mix($c[0], 255, $t), mix($c[1], 244, $t), mix($c[2], 233, $t)) };
        substr($out, $o, 3) = pack('C3', map { byte($_) } @v);
    }
    $out;
}
sub isMagenta { my ($r, $g, $b) = @_; $r > $g + 20 && $b > $g + 20 && $r + $b > 100 && $b >= 0.78 * $r && $r >= 0.78 * $b }
sub isMagentaLoose { my ($r, $g, $b) = @_; $r > $g + 20 && $b > $g + 20 && $r + $b > 100 }
sub magentaMask {
    my ($ref) = @_; my (%m, %loose);
    for (my $o = 0; $o < length $$ref; $o += 4) {
        my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $o, 4)); next if $a == 0;
        if (isMagenta($r, $g, $b)) { $m{$o} = 1 } elsif (isMagentaLoose($r, $g, $b)) { $loose{$o} = 1 }
    }
    for (1 .. 2) {
        my @add;
        for my $o (keys %loose) { for my $d (-4, 4, -$W * 4, $W * 4) { if ($m{ $o + $d }) { push @add, $o; last } } }
        for (@add) { $m{$_} = 1; delete $loose{$_} }
    }
    [sort { $a <=> $b } keys %m];
}
sub tint {
    my ($ref, $mask, $hex) = @_; my @c = map { hex(substr($hex, $_ * 2, 2)) } 0 .. 2; my $out = $$ref;
    for my $o (@$mask) {
        my ($r, $g, $b) = unpack('C3', substr($out, $o, 3));
        my $sh = .52 + .48 * ($r > $b ? $r : $b) / 255; my $hi = $g / 255 * .35;
        substr($out, $o, 3) = pack('C3', byte(mix($c[0] * $sh, 255, $hi)), byte(mix($c[1] * $sh, 244, $hi)), byte(mix($c[2] * $sh, 233, $hi)));
    }
    $out;
}
sub cell {
    my ($ref, $x0, $y0, $x1, $y1, $mul) = @_; my $c = '';
    if ($mul) {
        my ($cw, $ch) = (($x1 - $x0) * $mul, ($y1 - $y0) * $mul);
        for my $y (0 .. $ch - 1) { for my $x (0 .. $cw - 1) {
            my ($r, $g, $b, $a) = unpack('C4', substr($$ref, (($y0 + int($y / $mul)) * $W + $x0 + int($x / $mul)) * 4, 4)); my $t = $a / 255;
            $c .= pack('C4', int($r * $t + 247 * (1 - $t) + 0.5), int($g * $t + 246 * (1 - $t) + 0.5), int($b * $t + 242 * (1 - $t) + 0.5), 255);
        } }
        return [$cw, $ch, $c];
    }
    my $cw = int(($x1 - $x0) / 2); my $ch = int(($y1 - $y0) / 2);
    for my $y (0 .. $ch - 1) { for my $x (0 .. $cw - 1) {
        my ($r, $g, $b, $a) = unpack('C4', substr($$ref, (($y0 + $y * 2) * $W + $x0 + $x * 2) * 4, 4)); my $t = $a / 255;
        $c .= pack('C4', int($r * $t + 247 * (1 - $t) + 0.5), int($g * $t + 246 * (1 - $t) + 0.5), int($b * $t + 242 * (1 - $t) + 0.5), 255);
    } }
    [$cw, $ch, $c];
}
sub grid {
    my ($file, $rows) = @_; my $gap = 8; my $cols = 0; $cols = @$_ > $cols ? scalar @$_ : $cols for @$rows;
    my @rh = map { my $m = 0; for my $c (@$_) { $m = $c->[1] if $c->[1] > $m } $m } @$rows; my $cw = $rows->[0][0][0];
    my $GW = $cols * ($cw + $gap) + $gap; my $GH = $gap; $GH += $_ + $gap for @rh; my $g = pack('C4', 255, 255, 255, 255) x ($GW * $GH); my $oy = $gap;
    for my $r (0 .. $#$rows) { for my $c (0 .. $#{ $rows->[$r] }) {
        my $cell = $rows->[$r][$c]; my $ox = $gap + $c * ($cw + $gap);
        for my $y (0 .. $cell->[1] - 1) { substr($g, (($oy + $y) * $GW + $ox) * 4, $cell->[0] * 4) = substr($cell->[2], $y * $cell->[0] * 4, $cell->[0] * 4) }
    } $oy += $rh[$r] + $gap }
    savePng($file, $GW, $GH, $g); print "preview $file ${GW}x$GH\n";
}
sub rectLine {
    my ($ref, $z) = @_; my $red = pack('C4', 255, 0, 0, 255);
    for my $x ($z->[0] .. $z->[2] - 1) { for my $y ($z->[1], $z->[3] - 1) { substr($$ref, ($y * $W + $x) * 4, 4) = $red } }
    for my $y ($z->[1] .. $z->[3] - 1) { for my $x ($z->[0], $z->[2] - 1) { substr($$ref, ($y * $W + $x) * 4, 4) = $red } }
}
sub clampZone { my ($z) = @_; [$z->[0] < 0 ? 0 : $z->[0], $z->[1] < 0 ? 0 : $z->[1], $z->[2] > $W ? $W : $z->[2], $z->[3] > $H ? $H : $z->[3]] }

mkdirs($tmp);
my %layerCache;
sub layer { my ($png) = @_; return undef unless $faces && -f $png; $layerCache{$png} ||= do { my ($w, $h, $d) = load($png); die "$png is ${w}x$h" unless $w == $W && $h == $H; $d } }
my $made = 0;
for my $g (@gs) {
    my (@rowA, @rowB, @rowC, @rowD);
    for my $n (@ids) {
        my $name = sprintf('%s_outfit_%02d', $g, $n);
        my ($w, $h, $d) = load("$src/templates/$name.png");
        die "$name is ${w}x$h, expected $RECT[2]x$RECT[3]" unless $w == $RECT[2] && $h == $RECT[3];
        my $canvas = place($d, $w, $h, $RECT[0], $RECT[1]);
        my $mm = magentaMask(\$canvas); my $magenta = @$mm >= 300;
        my ($mask, $zones, $paint);
        if ($magenta) {
            ($mask, $zones) = ($mm, []);
            $paint = sub { tint(\$canvas, $mm, $_[0]) };
            printf "%s: 마젠타 재질 %d px → Tint\n", $name, scalar @$mm;
        } else {
            my $blobs = blobs(\$canvas);
            printf "%s 씨앗 덩어리: %s\n", $name, join('  ', map { sprintf('%dpx[%d,%d,%d,%d]', @$_) } grep { $_->[0] >= 40 } @$blobs) if $probe;
            $zones = $ZONES{$g}{$n} || [map { clampZone([$_->[1] - $PAD, $_->[2] - $PAD, $_->[3] + $PAD, $_->[4] + $PAD]) } grep { $_->[0] >= $MIN_BLOB } @$blobs];
            die "$name: 피부 구역이 없다" unless @$zones;
            ($mask, my $seed) = skinMask(\$canvas, $zones);
            $paint = sub { recolor(\$canvas, $mask, $_[0]) };
            my $dropped = 0; $dropped += $_->[0] for grep { $_->[0] < $MIN_BLOB } @$blobs;
            printf "%s: 구운 피부 — 구역 %d개(%s), 피부 %d px(씨앗 %d + 번짐 %d), 버린 작은 씨앗 %d px\n", $name, scalar @$zones, $ZONES{$g}{$n} ? '손으로' : '자동', scalar @$mask, $seed, @$mask - $seed, $ZONES{$g}{$n} ? 0 : $dropped;
        }
        if ($preview) {
            my @view = (190, 520, 610, 1180);
            push @rowA, cell(\$canvas, @view);
            my $all = $canvas; my $lime = pack('C3', 60, 255, 60); substr($all, $_, 3) = $lime for @$mask;
            my $boxed = $all; rectLine(\$boxed, $_) for @$zones;
            push @rowB, cell(\$boxed, @view);
            my $deep;
            for my $s (5, 0) {
                my $re = $paint->($SKIN[$s]); my $stack = $re; $deep = $re if $s == 5;
                my $hairN = $g eq 'f' ? 5 : 4;
                for my $l ("$faces/png/$g/faces/face_0_skin_$s.png", "$faces/png/$g/hair/hair_${hairN}_color_0_front.png") { my $ld = layer($l); over(\$stack, $ld) if $ld }
                push @{ $s == 5 ? \@rowC : \@rowD }, cell(\$stack, 190, 200, 610, 1180);
            }
            my $k = 0;
            for my $zz (grep { /^$g:$n:/ } @zoom) {
                my (undef, undef, $box) = split /:/, $zz; my @b = split /,/, $box;
                grid(sprintf('%s_zoom_%s%02d_%d.png', $preview, $g, $n, $k++), [[cell(\$canvas, @b, 4), cell(\$all, @b, 4), cell(\$deep, @b, 4)]]);
            }
        }
        next unless $write;
        mkdirs("$dst/$g/outfits"); mkdirs("$pngOut/png/$g/outfits");
        for my $s (@skins) {
            my $png = sprintf('%s/png/%s/outfits/outfit_%02d_skin_%d.png', $pngOut, $g, $n, $s);
            savePng($png, $W, $H, $paint->($SKIN[$s]));
            run($cwebp, '-quiet', '-resize', $W / 2, $H / 2, '-q', '85', '-m', '6', wp($png), '-o', wp(sprintf('%s/%s/outfits/outfit_%02d_skin_%d.webp', $dst, $g, $n, $s)));
            $made++;
        }
    }
    if ($preview && @rowA) {
        my $half = int((@ids + 1) / 2);
        grid("${preview}_${g}_a.png", [[@rowA[0 .. $half - 1]], [@rowB[0 .. $half - 1]], [@rowC[0 .. $half - 1]], [@rowD[0 .. $half - 1]]]);
        grid("${preview}_${g}_b.png", [[@rowA[$half .. $#rowA]], [@rowB[$half .. $#rowB]], [@rowC[$half .. $#rowC]], [@rowD[$half .. $#rowD]]]) if @ids > $half;
    }
}
print "WebP $made장 → $dst\n" if $write;
