"""Read a bounded log tail and group warnings/errors without repeating stack traces."""
import argparse
from collections import Counter, deque
import json
from pathlib import Path
import re


ANSI = re.compile(r"\x1b\[[0-9;]*m")
HEADER = re.compile(
    r"^(?P<time>\d{4}-\d{2}-\d{2}T\S+|\d{2}:\d{2}:\d{2})\s+"
    r"(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)\s+"
    r"(?:\[[^\]]*\]\s+)?(?P<body>.*)$"
)
FILE_HEADER = re.compile(r"^(?P<time>\d{4}-\d{2}-\d{2}T\S+)\s+(?P<body>\S.*? - .*)$")
EXCEPTION = re.compile(r"^(?:Caused by: )?[\w.$]+(?:Exception|Error)(?::|$)")


def normalize(value):
    value = re.sub(r"\b0x[\da-fA-F]+\b", "<address>", value)
    value = re.sub(r"\b[\da-fA-F]{8}-[\da-fA-F-]{27,}\b", "<uuid>", value)
    return re.sub(r"(?<![\w])\d+(?:\.\d+)?(?![\w])", "#", value)


def read_tail(path, max_bytes, max_lines):
    # Read sharing works with the active Logback file; never request an exclusive handle.
    with path.open('rb') as stream:
        size = stream.seek(0, 2)
        start = max(0, size - max_bytes)
        stream.seek(start)
        if start:
            stream.readline()  # discard a partially captured UTF-8 line
        data = stream.read(max_bytes)
    lines = deque(data.decode('utf-8', errors='replace').splitlines(), maxlen=max_lines)
    return list(lines), start > 0 or data.count(b'\n') > max_lines


def summarize(path, max_bytes, max_lines, limit, frames):
    lines, limited = read_tail(path, max_bytes, max_lines)
    default_level = 'ERROR' if 'error' in path.name.lower() else 'WARN' if 'warn' in path.name.lower() else None
    events = []
    current = None
    timestamps = []
    stack_lines = 0
    for line in lines:
        line = ANSI.sub('', line)
        match = HEADER.match(line)
        fallback = None if match or not default_level else FILE_HEADER.match(line)
        if match or fallback:
            event = match or fallback
            timestamps.append(event['time'])
            level = match['level'] if match else default_level
            current = None
            if level not in ('WARN', 'ERROR'):
                continue
            body = event['body']
            logger, separator, message = body.partition(' - ')
            if not separator:
                logger, message = '', body
            current = dict(time=event['time'], level=level, logger=logger, message=message,
                           cause='', frames=[])
            events.append(current)
        elif current:
            stripped = line.strip()
            if EXCEPTION.match(stripped):
                current['cause'] = stripped  # deepest visible cause wins
            elif stripped.startswith('at '):
                stack_lines += 1
                if len(current['frames']) < frames:
                    current['frames'].append(stripped)
    groups = {}
    for event in events:
        key = (event['level'], event['logger'], normalize(event['message']), normalize(event['cause']))
        if key not in groups:
            groups[key] = dict(count=0, level=event['level'], logger=event['logger'],
                               message=event['message'], cause=event['cause'], frames=event['frames'],
                               first=event['time'], last=event['time'])
        groups[key]['count'] += 1
        groups[key]['last'] = event['time']
    ordered = sorted(groups.values(), key=lambda g: (g['level'] != 'ERROR', -g['count'], g['first']))
    return dict(path=str(path.resolve()), scannedLines=len(lines), tailLimited=limited,
                first=next(iter(timestamps), None), last=timestamps[-1] if timestamps else None,
                events=dict(Counter(e['level'] for e in events)), stackFrames=stack_lines,
                distinctGroups=len(groups), shownGroups=ordered[:limit])


def positive(value):
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError('must be positive')
    return parsed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path, help='Console, warnings or errors log; original and repack paths are explicit')
    parser.add_argument('--max-mib', type=positive, default=8, help='Maximum bytes read from the end (default: 8 MiB)')
    parser.add_argument('--tail-lines', type=positive, default=30000)
    parser.add_argument('--limit', type=positive, default=8, help='Maximum groups shown; errors first')
    parser.add_argument('--frames', type=int, choices=range(0, 11), default=3)
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    try:
        report = summarize(args.log, args.max_mib * 1024 * 1024, args.tail_lines, args.limit, args.frames)
    except OSError as error:
        parser.exit(1, 'Could not read log: ' + str(error) + '\n')
    if args.json:
        print(json.dumps(report, indent=2))
        return
    print(report['path'])
    print('Captured log interval:', report['first'], 'to', report['last'])
    print('Read {} lines{}; {} ERROR, {} WARN; {} groups; {} stack frames.'.format(
        report['scannedLines'], ' (bounded tail)' if report['tailLimited'] else '',
        report['events'].get('ERROR', 0), report['events'].get('WARN', 0),
        report['distinctGroups'], report['stackFrames']))
    if not report['shownGroups']:
        print('No recognized warning/error entries in the captured tail.')
    for group in report['shownGroups']:
        print('\n{} x {} {} [{} to {}]'.format(group['count'], group['level'], group['logger'], group['first'], group['last']))
        print(' ', group['message'][:700])
        if group['cause']:
            print(' ', group['cause'][:700])
        for frame in group['frames']:
            print(' ', frame[:300])
    if report['distinctGroups'] > len(report['shownGroups']):
        print('\n{} additional groups omitted; increase --limit if needed.'.format(report['distinctGroups'] - len(report['shownGroups'])))
    if report['tailLimited']:
        print('Counts apply to this tail only. Its first trace may be incomplete.')


if __name__ == '__main__':
    main()
