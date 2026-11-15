#!/usr/bin/perl
use strict; use warnings;
use Compress::Zlib ();
my ($bin, $v3, $dst, $pngOut, $tmp, @opt) = @ARGV;
die "usage: gpt_pieces.pl <libwebp bin> <v3 dir> <pixel assets dir> <png out dir> <tmp dir> [--preview <prefix>] [--no-write] [--skins ..] [--ids ..] [--parts ..] [--hairs ..] [--legs-all]\n" unless defined $tmp;
my ($preview, $write, $legsAll, %o) = (undef, 1, 0);
while (@opt) { my $k = shift @opt; if ($k eq '--preview') { $preview = shift @opt } elsif ($k eq '--no-write') { $write = 0 } elsif ($k eq '--legs-all') { $legsAll = 1 } elsif ($k =~ /^--(skins|ids|parts|hairs)$/) { $o{$1} = [split /,/, shift @opt] } else { die "unknown option $k\n" } }
my @skins = @{ $o{skins} || [0 .. 8] }; my @ids = @{ $o{ids} || [0 .. 19] }; my @parts = @{ $o{parts} || [qw(top bottom shoes)] }; my @hairs = @{ $o{hairs} || [6 .. 9] };
my ($dwebp, $cwebp) = ("$bin/dwebp.exe", "$bin/cwebp.exe");
my ($W, $H) = (800, 1200);
my $wp = sub { my $p = shift; $p =~ s{^/([a-zA-Z])/}{\U$1\E:/}; $p };
my @SKIN = qw(FFE8D8 F4CDAE E3B28E C18B63 955F42 633D2E FFF3EA 4C2E22 3A231B);
my @HAIR = qw(2D2928 6A4631 995A3C 80716B C5A060 B5B7C0 B5788C 43546B);
my %BAND = (
    f => { top => [160, 520, 480, 360], bottom => [180, 795, 440, 245], shoes => [180, 950, 440, 175] },
    m => { top => [160, 520, 480, 360], bottom => [180, 780, 440, 300], shoes => [180, 960, 440, 220] },
);
my %HAIR_RECT = (f => [100, 80, 600, 555], m => [150, 100, 500, 425]);
my %TRIM = (
    f => { shoes => { 0 => 1040, 1 => 1004, 2 => 1004, 3 => 1004, 4 => 1004, 5 => 1034, 6 => 1040, 7 => 993, 8 => 1040, 9 => 1002,
                      10 => 1040, 11 => 1001, 12 => 1040, 13 => 990, 14 => 998, 15 => 1007, 16 => 1013, 17 => 958, 18 => 1040, 19 => 1040 } },
    m => { shoes => { 0 => 1073, 1 => 1066, 2 => 1069, 3 => 1074, 4 => 1073, 5 => 1073, 6 => 1064, 7 => 1076, 8 => 1075, 9 => 1065,
                      10 => 1073, 11 => 1008, 12 => 1072, 13 => 1076, 14 => 1068, 15 => 1080, 16 => 1074, 17 => 1080, 18 => 1068, 19 => 1080 } },
);
my %LEGS = (
    f => { bottom => { 1 => 912, 2 => 912, 3 => 912, 4 => 912, 7 => 958, 9 => 912, 11 => 970, 17 => 912 },
           shoes  => { 1 => 950, 2 => 950, 3 => 950, 4 => 950, 7 => 950, 9 => 950, 11 => 970, 17 => 950 } },
    m => { bottom => { 11 => 965, 18 => 912 }, shoes => { 11 => 965, 18 => 960 } },
);
my %SOCKS = (f => { 1 => 1000, 2 => 1000, 3 => 1000, 4 => 1000, 7 => 990, 9 => 1000, 11 => 1000 }, m => { 11 => 1005 });
my @ZONE_NECK = ([350, 540, 445, 632]);
my @ZONE_HANDS = ([222, 820, 300, 912], [498, 820, 576, 912]);
my ($LEG_X0, $LEG_X1, $LEG_Y1, $FEET_Y1) = (280, 520, 1080, 1110);
my %EXTRA = (
    'f/top/17' => [[222, 690, 310, 912], [490, 690, 576, 912]],
    'f/top/19' => [[340, 733, 460, 762]],
);
my %ZONES_OVERRIDE = ('f/top/16' => [[218, 684, 300, 760], [500, 684, 582, 760]]);
sub zonesFor {
    my ($g, $part, $n) = @_;
    my $key = "$g/$part/$n";
    return $ZONES_OVERRIDE{$key} if $ZONES_OVERRIDE{$key};
    return [@ZONE_NECK, @ZONE_HANDS, @{ $EXTRA{$key} || [] }] if $part eq q{top};
    my $y0 = $legsAll ? ($part eq q{bottom} ? 912 : ($g eq q{f} ? 950 : 960)) : $LEGS{$g}{$part}{$n};
    my @legs = defined $y0 ? ([$LEG_X0, $y0, $LEG_X1, $part eq q{bottom} ? $LEG_Y1 : $FEET_Y1]) : ();
    return $part eq q{bottom} ? [@ZONE_HANDS, @legs] : [@legs];
}

sub run { system(@_) == 0 or die "failed: @_\n" }
sub load {
    my ($png) = @_;
    my ($lw, $pam) = ("$tmp/gpt_in.webp", "$tmp/gpt_in.pam");
    run($cwebp, '-quiet', '-lossless', '-exact', $wp->($png), '-o', $wp->($lw));
    run($dwebp, '-quiet', '-pam', $wp->($lw), '-o', $wp->($pam));
    open my $fh, '<:raw', $pam or die "$pam: $!"; local $/; my $d = <$fh>; close $fh;
    $d =~ s/^P7\s+WIDTH\s+(\d+)\s+HEIGHT\s+(\d+)\s+DEPTH\s+4\s+MAXVAL\s+255\s+TUPLTYPE\s+RGB_ALPHA\s+ENDHDR\s//s or die "bad pam header: $png";
    return ($1, $2, $d);
}
sub chunk { my ($t, $d) = @_; pack('N', length $d) . $t . $d . pack('N', Compress::Zlib::crc32($t . $d)) }
sub savePng {
    my ($path, $w, $h, $d, $level) = @_;
    my $raw = ''; $raw .= "\0" . substr($d, $_ * $w * 4, $w * 4) for 0 .. $h - 1;
    open my $fh, '>:raw', $path or die "$path: $!";
    print $fh "\x89PNG\r\n\x1a\n", chunk('IHDR', pack('NNCCCCC', $w, $h, 8, 6, 0, 0, 0)), chunk('IDAT', Compress::Zlib::compress($raw, $level // 6)), chunk('IEND', '');
    close $fh;
}
sub mkdirs { my ($p) = @_; my $acc = ''; for my $part (split m{/}, $p) { $acc .= "$part/"; mkdir $acc unless -d $acc } }
sub roundEven { my ($v) = @_; my $f = int($v); my $r = $v - $f; return $r > 0.5 ? $f + 1 : $r < 0.5 ? $f : ($f % 2 ? $f + 1 : $f) }
sub byte { my ($v) = @_; $v = roundEven($v); $v < 0 ? 0 : $v > 255 ? 255 : $v }
sub mix { my ($a, $b, $t) = @_; $t = $t < 0 ? 0 : $t > 1 ? 1 : $t; $a + ($b - $a) * $t }
sub blank { pack('C4', 0, 0, 0, 0) x ($W * $H) }
sub over {
    my ($ref, $src, $w, $h, $x, $y) = @_;
    for my $row (0 .. $h - 1) {
        my $dy = $y + $row; next if $dy < 0 || $dy >= $H;
        for my $col (0 .. $w - 1) {
            my $dx = $x + $col; next if $dx < 0 || $dx >= $W;
            my $so = ($row * $w + $col) * 4; my $sa = ord(substr($src, $so + 3, 1)); next if $sa == 0;
            my $do = ($dy * $W + $dx) * 4;
            if ($sa == 255) { substr($$ref, $do, 4) = substr($src, $so, 4); next }
            my ($sr, $sg, $sb) = unpack('C3', substr($src, $so, 3)); my ($dr, $dg, $db, $da) = unpack('C4', substr($$ref, $do, 4));
            my $fa = $sa / 255; my $fd = $da / 255 * (1 - $fa); my $oa = $fa + $fd;
            substr($$ref, $do, 4) = pack('C4', int(($sr * $fa + $dr * $fd) / $oa + 0.5), int(($sg * $fa + $dg * $fd) / $oa + 0.5), int(($sb * $fa + $db * $fd) / $oa + 0.5), int($oa * 255 + 0.5));
        }
    }
}
my $LBASE = .2126 * 239 + .7152 * 201 + .0722 * 173;
sub isSkin {
    my ($r, $g, $b) = @_;
    return 0 unless $r >= 150 && $g >= 100 && $b >= 60;
    my $rb = $r - $b; return 0 unless $rb >= 46 && $rb <= 95;
    my $q = ($g - $b) / $rb; return 0 unless $q >= 0.36 && $q <= 0.50;
    my $sat = $rb / $r; return 0 unless $sat >= 0.22 && $sat <= 0.45;
    return 0 if $r > 245 && $g > 235 && $b > 225;
    1;
}
sub skinMask {
    my ($ref, $zones) = @_; my @m;
    for my $z (@$zones) { for my $y ($z->[1] .. $z->[3] - 1) { for my $x ($z->[0] .. $z->[2] - 1) {
        my $o = ($y * $W + $x) * 4; my ($r, $g, $b, $a) = unpack('C4', substr($$ref, $o, 4)); next if $a < 8; push @m, $o if isSkin($r, $g, $b);
    } } }
    \@m;
}
sub extrudeLegs {
    my ($ref, $g, $sy, $mask) = @_; my %in = map { $_ => 1 } @$mask;
    my $yEnd = $BAND{$g}{bottom}[1] + $BAND{$g}{bottom}[3];
    my ($ry, $best) = ($sy - 2, -1);
    for my $y ($sy - 30 .. $sy - 1) { my $c = grep { $in{ ($y * $W + $LEG_X0 + $_) * 4 } } 0 .. $LEG_X1 - $LEG_X0 - 1; ($ry, $best) = ($y, $c) if $c > $best }
    my $slice = substr($$ref, ($ry * $W + $LEG_X0) * 4, ($LEG_X1 - $LEG_X0) * 4);
    my @skinX = grep { $in{ ($ry * $W + $LEG_X0 + $_) * 4 } } 0 .. $LEG_X1 - $LEG_X0 - 1;
    for my $y ($ry + 1 .. $yEnd - 1) {
        substr($$ref, ($y * $W + $LEG_X0) * 4, length $slice) = $slice;
        $in{ ($y * $W + $LEG_X0 + $_) * 4 } = 1 for @skinX;
    }
    printf "   legs extruded from row %d to y%d (%d skin px/row)\n", $ry, $yEnd - 1, scalar @skinX;
    [sort { $a <=> $b } keys %in];
}
sub withOutline {
    my ($ref, $zones, $mask) = @_; my %in = map { $_ => 1 } @$mask; my @add;
    for my $z (@$zones) { for my $y ($z->[1] .. $z->[3] - 1) { for my $x ($z->[0] .. $z->[2] - 1) {
        my $o = ($y * $W + $x) * 4; next if $in{$o}; my ($pr, $pg, $pb, $a) = unpack('C4', substr($$ref, $o, 4)); next if $a < 8;
        next unless .2126 * $pr + .7152 * $pg + .0722 * $pb < 110;
        my ($skin, $cloth) = (0, 0);
        for my $d (-4, 4, -$W * 4, $W * 4) {
            my $q = $o + $d; next if $q < 0 || $q + 4 > length $$ref;
            if ($in{$q}) { $skin = 1; next }
            my ($qr, $qg, $qb, $qa) = unpack('C4', substr($$ref, $q, 4)); next if $qa < 8;
            $cloth = 1 if .2126 * $qr + .7152 * $qg + .0722 * $qb >= 110;
        }
        push @add, $o if $skin && !$cloth;
    } } }
    $in{$_} = 1 for @add;
    [sort { $a <=> $b } keys %in];
}
sub recolor {
    my ($ref, $mask, $hex) = @_; my @c = map { hex(substr($hex, $_ * 2, 2)) } 0 .. 2; my $out = $$ref;
    for my $o (@$mask) {
        my ($r, $g, $b) = unpack('C3', substr($out, $o, 3)); my $s = (.2126 * $r + .7152 * $g + .0722 * $b) / $LBASE;
        my @v = $s <= 1 ? (map { $_ * $s } @c) : do { my $t = ($s - 1) / 0.25; (mix($c[0], 255, $t), mix($c[1], 244, $t), mix($c[2], 233, $t)) };
        substr($out, $o, 3) = pack('C3', map { byte($_) } @v);
    }
    $out;
}
sub clothOnly { my ($ref, $mask) = @_; my $out = $$ref; substr($out, $_, 4) = "\0\0\0\0" for @$mask; $out }
sub skinOnly { my ($ref, $mask) = @_; my $out = blank(); substr($out, $_, 4) = substr($$ref, $_, 4) for @$mask; $out }
sub tintHair {
    my ($d, $hex) = @_; my @c = map { hex(substr($hex, $_ * 2, 2)) } 0 .. 2; my $out = '';
    for (my $o = 0; $o < length $d; $o += 4) {
        my ($r, $g, $b, $a) = unpack('C4', substr($d, $o, 4));
        if ($a) {
            my $t = (.2126 * $r + .7152 * $g + .0722 * $b - 22) / 155; $t = $t < 0 ? 0 : $t > 1 ? 1 : $t;
            my $t1 = $t / .55; $t1 = 1 if $t1 > 1; my $t2 = ($t - .55) / .45; $t2 = 0 if $t2 < 0;
            ($r, $g, $b) = $t <= .55
                ? (byte(mix(29, $c[0], $t1)), byte(mix(23, $c[1], $t1)), byte(mix(22, $c[2], $t1)))
                : (byte(mix($c[0], $c[0] * .55 + 255 * .45, $t2)), byte(mix($c[1], $c[1] * .55 + 238 * .45, $t2)), byte(mix($c[2], $c[2] * .55 + 218 * .45, $t2)));
        }
        $out .= pack('C4', $r, $g, $b, $a);
    }
    $out;
}
sub cell {
    my ($ref, $x0, $y0, $x1, $y1) = @_; my $cw = int(($x1 - $x0) / 2); my $ch = int(($y1 - $y0) / 2); my $c = '';
    for my $y (0 .. $ch - 1) { for my $x (0 .. $cw - 1) {
        my ($r, $g, $b, $a) = unpack('C4', substr($$ref, (($y0 + $y * 2) * $W + $x0 + $x * 2) * 4, 4)); my $t = $a / 255;
        $c .= pack('C4', int($r * $t + 247 * (1 - $t) + 0.5), int($g * $t + 246 * (1 - $t) + 0.5), int($b * $t + 242 * (1 - $t) + 0.5), 255);
    } }
    [$cw, $ch, $c];
}
sub grid {
    my ($file, $rows) = @_; my $gap = 8; my ($cw, $ch) = ($rows->[0][0][0], $rows->[0][0][1]); my $cols = 0; $cols = @$_ > $cols ? scalar @$_ : $cols for @$rows;
    my $GW = $cols * ($cw + $gap) + $gap; my $GH = @$rows * ($ch + $gap) + $gap; my $g = pack('C4', 255, 255, 255, 255) x ($GW * $GH);
    for my $r (0 .. $#$rows) { for my $c (0 .. $#{ $rows->[$r] }) {
        my $cell = $rows->[$r][$c]; my ($ox, $oy) = ($gap + $c * ($cw + $gap), $gap + $r * ($ch + $gap));
        for my $y (0 .. $cell->[1] - 1) { substr($g, (($oy + $y) * $GW + $ox) * 4, $cell->[0] * 4) = substr($cell->[2], $y * $cell->[0] * 4, $cell->[0] * 4) }
    } }
    savePng($file, $GW, $GH, $g); print "preview $file ${GW}x$GH\n";
}
my %pieceCache;
sub piece {
    my ($g, $part, $n) = @_; my $key = "$g/$part/$n";
    return $pieceCache{$key} if $pieceCache{$key};
    my $r = $BAND{$g}{$part};
    my ($w, $h, $d) = load(sprintf('%s/templates/%s_%s_%02d.png', $v3, $g, $part, $n));
    die sprintf("%s %s %02d is %dx%d, expected %dx%d\n", $g, $part, $n, $w, $h, $r->[2], $r->[3]) unless $w == $r->[2] && $h == $r->[3];
    my $canvas = blank(); over(\$canvas, $d, $w, $h, $r->[0], $r->[1]);
    if (my $cut = $TRIM{$g}{$part}{$n}) { my $rows = $cut - $r->[1]; substr($canvas, $r->[1] * $W * 4, $rows * $W * 4) = "\0" x ($rows * $W * 4) if $rows > 0 }
    $pieceCache{$key} = $canvas;
}
my %REGION = (top => [160, 520, 640, 880], bottom => [180, 780, 620, 1080], shoes => [180, 950, 620, 1180]);

my @pskins = grep { $_ == 1 || $_ == 5 } @skins; @pskins = @skins[0 .. ($#skins < 1 ? $#skins : 1)] unless @pskins;
my (%prev, @hairRows, %masks);
for my $g (qw(f m)) {
    for my $part (@parts) {
        for my $n (@ids) {
            my $canvas = piece($g, $part, $n);
            my $mask = skinMask(\$canvas, zonesFor($g, $part, $n));
            if ($part eq q{bottom} && $SOCKS{$g}{$n}) { $mask = extrudeLegs(\$canvas, $g, $SOCKS{$g}{$n}, $mask); $pieceCache{"$g/$part/$n"} = $canvas }
            $mask = withOutline(\$canvas, zonesFor($g, $part, $n), $mask) if $part ne q{top};
            $masks{"$g/$part/$n"} = $mask;
            printf "%s %-6s %02d: skin pixels %d\n", $g, $part, $n, scalar @$mask;
            my $split = $part ne q{top};
            if ($split && $write) {
                mkdirs("$dst/$g/$part"); mkdirs("$pngOut/png/$g/$part");
                my $png = sprintf('%s/png/%s/%s/%s_%02d_cloth.png', $pngOut, $g, $part, $part, $n);
                savePng($png, $W, $H, clothOnly(\$canvas, $mask), 1);
                run($cwebp, '-quiet', '-resize', $W / 2, $H / 2, '-q', '85', '-m', '4', $wp->($png), '-o', $wp->(sprintf('%s/%s/%s/%s_%02d_cloth.webp', $dst, $g, $part, $part, $n)));
            }
            for my $s (@skins) {
                my $img = recolor(\$canvas, $mask, $SKIN[$s]);
                if ($preview && grep { $_ == $s } @pskins) { my $rg = $REGION{$part}; push @{ $prev{"${g}_$part"}{$s} }, cell(\$img, @$rg) }
                next unless $write;
                mkdirs("$dst/$g/$part"); mkdirs("$pngOut/png/$g/$part");
                my $png = sprintf('%s/png/%s/%s/%s_%02d_skin_%d.png', $pngOut, $g, $part, $part, $n, $s);
                savePng($png, $W, $H, $split ? skinOnly(\$img, $mask) : $img, 1);
                run($cwebp, '-quiet', '-resize', $W / 2, $H / 2, '-q', '85', '-m', '4', $wp->($png), '-o', $wp->(sprintf('%s/%s/%s/%s_%02d_skin_%d.webp', $dst, $g, $part, $part, $n, $s)));
            }
            delete $pieceCache{"$g/$part/$n"} unless $preview;
        }
    }
    for my $hn (@hairs) {
        my $r = $HAIR_RECT{$g};
        my ($w, $h, $d) = load("$v3/templates/${g}_hair_${hn}_front.png");
        die "hair $g $hn is ${w}x$h, expected $r->[2]x$r->[3]\n" unless $w == $r->[2] && $h == $r->[3];
        my @row;
        for my $c (0 .. 7) {
            my $canvas = blank(); over(\$canvas, tintHair($d, $HAIR[$c]), $w, $h, $r->[0], $r->[1]);
            push @row, cell(\$canvas, 80, 60, 720, 680) if $preview && ($c == 0 || $c == 4 || $c == 6);
            next unless $write;
            mkdirs("$dst/$g/hair"); mkdirs("$pngOut/png/$g/hair");
            my $png = "$pngOut/png/$g/hair/hair_${hn}_color_${c}_front.png";
            savePng($png, $W, $H, $canvas, 1);
            run($cwebp, '-quiet', '-resize', $W / 2, $H / 2, '-q', '85', '-m', '4', $wp->($png), '-o', $wp->("$dst/$g/hair/hair_${hn}_color_${c}_front.webp"));
        }
        push @hairRows, \@row if @row;
        print "$g hair $hn: 8 colors\n";
    }
}
if ($preview) {
    for my $g (qw(f m)) { for my $part (@parts) {
        my $p = $prev{"${g}_$part"} or next; my @rows;
        for my $half (0, 1) { for my $s (@pskins) { my @cells = @{ $p->{$s} }; my @r = @cells[grep { $_ >= 0 && $_ < @cells } ($half * 10) .. ($half * 10 + 9)]; push @rows, \@r if @r } }
        grid("${preview}_${g}_$part.png", \@rows) if @rows;
    } }
    if (@parts == 3 && @ids == 20) {
        my %COMBOS = (
            f => [[[0, 0, 0], [7, 7, 7], [12, 12, 12], [19, 19, 19], [7, 12, 16], [12, 14, 3]], [[0, 0, 2], [2, 2, 17], [5, 12, 1], [4, 13, 3], [19, 9, 0], [1, 11, 13]]],
            m => [[[0, 0, 0], [7, 7, 7], [12, 12, 12], [19, 19, 19], [7, 12, 16], [12, 14, 3]], [[0, 18, 0], [4, 6, 0], [11, 11, 17], [3, 18, 11], [8, 2, 9], [16, 19, 18]]],
        );
        my @rows;
        for my $g (qw(f m)) { for my $line (@{ $COMBOS{$g} }) {
            my @row;
            for my $combo (@$line) {
                my ($t, $b, $s) = @$combo; my $c = blank();
                my %img = map { my ($p, $i) = @$_; ($p => recolor(\(piece($g, $p, $i)), $masks{"$g/$p/$i"}, $SKIN[1])) } (['bottom', $b], ['shoes', $s], ['top', $t]);
                my ($mb, $ms) = ($masks{"$g/bottom/$b"}, $masks{"$g/shoes/$s"});
                for my $layer (skinOnly(\$img{bottom}, $mb), skinOnly(\$img{shoes}, $ms), clothOnly(\$img{shoes}, $ms), clothOnly(\$img{bottom}, $mb), $img{top}) { over(\$c, $layer, $W, $H, 0, 0) }
                push @row, cell(\$c, 150, 500, 650, 1150);
            }
            push @rows, \@row;
        } }
        grid("${preview}_stack.png", \@rows);
    }
    grid("${preview}_hair.png", \@hairRows) if @hairRows;
}
