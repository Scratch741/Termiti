# -*- coding: utf-8 -*-
"""
Nastaví listBiasY (náhledovka karty v seznamu balíčku) v CardPresentation.kt.

Použití:  python tools/set_list_bias.py C45=0.40 C44=0.25 078=-0.60 ...
Hodnotu v bloku CardPres(...) nahradí, pokud už existuje, jinak ji doplní na konec.
"""
import io, re, sys
sys.stdout.reconfigure(encoding='utf-8')

P = 'app/src/main/java/com/example/termiti/CardPresentation.kt'


def call_end(s, start):
    """Index uzavírací závorky volání CardPres( začínajícího na `start` (za '(')."""
    depth, i, in_str = 1, start, False
    while i < len(s):
        ch = s[i]
        if in_str:
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError('neuzavřené CardPres(')


def main(args):
    s = io.open(P, encoding='utf-8').read()
    for arg in args:
        cid, val = arg.split('=')
        v = float(val)
        lit = ('%.2f' % v) + 'f'
        head = '"%s" to CardPres(' % cid
        if s.count(head) != 1:
            raise SystemExit('%s: nalezeno %dx' % (cid, s.count(head)))
        a = s.index(head) + len(head)
        b = call_end(s, a)
        body = s[a:b]
        if re.search(r'listBiasY\s*=', body):
            new_body = re.sub(r'listBiasY\s*=\s*-?[0-9.]+f', 'listBiasY = ' + lit, body)
            how = 'změněno'
        else:
            new_body = body.rstrip() + ', listBiasY = ' + lit
            how = 'doplněno'
        s = s[:a] + new_body + s[b:]
        print('  %-4s listBiasY = %-7s (%s)' % (cid, lit, how))
    io.open(P, 'w', encoding='utf-8', newline='\n').write(s)


if __name__ == '__main__':
    main(sys.argv[1:])
