<?php

namespace Azuriom\Services\Prefix;

/**
 * Convert MiniMessage / legacy (& / §) prefix strings to escaped HTML for previews.
 */
class PrefixFormatter
{
    private const VANILLA = [
        '0' => '#000000', '1' => '#0000AA', '2' => '#00AA00', '3' => '#00AAAA',
        '4' => '#AA0000', '5' => '#AA00AA', '6' => '#FFAA00', '7' => '#AAAAAA',
        '8' => '#555555', '9' => '#5555FF', 'a' => '#55FF55', 'b' => '#55FFFF',
        'c' => '#FF5555', 'd' => '#FF55FF', 'e' => '#FFFF55', 'f' => '#FFFFFF',
    ];

    private const NAMED = [
        'black' => '#000000', 'dark_blue' => '#0000AA', 'dark_green' => '#00AA00',
        'dark_aqua' => '#00AAAA', 'dark_red' => '#AA0000', 'dark_purple' => '#AA00AA',
        'gold' => '#FFAA00', 'gray' => '#AAAAAA', 'dark_gray' => '#555555',
        'blue' => '#5555FF', 'green' => '#55FF55', 'aqua' => '#55FFFF',
        'red' => '#FF5555', 'light_purple' => '#FF55FF', 'yellow' => '#FFFF55',
        'white' => '#FFFFFF', 'grey' => '#AAAAAA',
    ];

    public function toHtml(?string $raw): string
    {
        if ($raw === null || $raw === '') {
            return '';
        }

        $input = str_replace(["\r", "\n"], '', str_replace('§', '&', $raw));
        $html = '';
        $styles = [];
        $offset = 0;
        $length = strlen($input);

        while ($offset < $length) {
            if ($input[$offset] === '<' && preg_match('/<(\/)?([^>]+)>/A', $input, $match, 0, $offset) === 1) {
                $this->applyMiniTag($styles, $match[1] === '/', trim($match[2]));
                $offset += strlen($match[0]);

                continue;
            }

            if ($input[$offset] === '&' && $offset + 1 < $length) {
                $consumed = $this->applyLegacy($styles, $input, $offset);
                if ($consumed > 0) {
                    $offset += $consumed;

                    continue;
                }
            }

            if (preg_match('/./usA', $input, $char, 0, $offset) !== 1) {
                break;
            }

            $html .= $this->wrap(htmlspecialchars($char[0], ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'), $styles);
            $offset += strlen($char[0]);
        }

        return $html;
    }

    /**
     * @param  array<string, string|bool>  $styles
     */
    private function applyMiniTag(array &$styles, bool $closing, string $tag): void
    {
        $lower = strtolower($tag);

        if ($closing) {
            if ($lower === 'reset' || $lower === 'r') {
                $styles = [];

                return;
            }

            $name = explode(':', $lower, 2)[0];
            if (in_array($name, ['color', 'colour', 'c', 'gradient', 'rainbow'], true) || isset(self::NAMED[$name])) {
                unset($styles['color']);

                return;
            }

            switch ($name) {
                case 'bold':
                case 'b':
                    unset($styles['bold']);
                    break;
                case 'italic':
                case 'i':
                case 'em':
                    unset($styles['italic']);
                    break;
                case 'underlined':
                case 'u':
                    unset($styles['underline']);
                    break;
                case 'strikethrough':
                case 'st':
                    unset($styles['strike']);
                    break;
                case 'obfuscated':
                case 'obf':
                    unset($styles['obfuscated']);
                    break;
            }

            return;
        }

        if ($lower === 'reset' || $lower === 'r') {
            $styles = [];

            return;
        }

        if (isset(self::NAMED[$lower])) {
            $styles['color'] = self::NAMED[$lower];

            return;
        }

        if (preg_match('/^#([0-9a-f]{6})$/', $lower, $hex) === 1) {
            $styles['color'] = '#'.$hex[1];

            return;
        }

        if (preg_match('/^(?:color|colour|c):#?([0-9a-f]{6})$/', $lower, $hex) === 1) {
            $styles['color'] = '#'.$hex[1];

            return;
        }

        if (preg_match('/^gradient:([^>]+)$/', $lower, $grad) === 1) {
            $first = explode(':', $grad[1])[0];
            $styles['color'] = $this->resolveColorToken($first);

            return;
        }

        match ($lower) {
            'bold', 'b' => $styles['bold'] = true,
            'italic', 'i', 'em' => $styles['italic'] = true,
            'underlined', 'u' => $styles['underline'] = true,
            'strikethrough', 'st' => $styles['strike'] = true,
            'obfuscated', 'obf' => $styles['obfuscated'] = true,
            default => null,
        };
    }

    /**
     * @param  array<string, string|bool>  $styles
     */
    private function applyLegacy(array &$styles, string $input, int $i): int
    {
        $next = $input[$i + 1];

        if ($next === '#' && isset($input[$i + 7]) && preg_match('/^&#([0-9A-Fa-f]{6})/', substr($input, $i, 8), $hex) === 1) {
            $styles['color'] = '#'.$hex[1];

            return 8;
        }

        if (($next === 'x' || $next === 'X') && preg_match('/^&x(?:&([0-9A-Fa-f])){6}/', substr($input, $i, 14), $hex) === 1) {
            $digits = preg_replace('/[^0-9A-Fa-f]/', '', substr($input, $i + 2, 12));
            if (strlen($digits) === 6) {
                $styles['color'] = '#'.$digits;

                return 14;
            }
        }

        $code = strtolower($next);

        if (isset(self::VANILLA[$code])) {
            $styles['color'] = self::VANILLA[$code];

            return 2;
        }

        switch ($code) {
            case 'l':
                $styles['bold'] = true;

                return 2;
            case 'o':
                $styles['italic'] = true;

                return 2;
            case 'n':
                $styles['underline'] = true;

                return 2;
            case 'm':
                $styles['strike'] = true;

                return 2;
            case 'k':
                $styles['obfuscated'] = true;

                return 2;
            case 'r':
                $styles = [];

                return 2;
            default:
                return 0;
        }
    }

    /**
     * @param  array<string, string|bool>  $styles
     */
    private function wrap(string $escaped, array $styles): string
    {
        if ($styles === []) {
            return $escaped;
        }

        $css = [];
        if (isset($styles['color'])) {
            $css[] = 'color:'.$styles['color'];
        }
        if (! empty($styles['bold'])) {
            $css[] = 'font-weight:700';
        }
        if (! empty($styles['italic'])) {
            $css[] = 'font-style:italic';
        }

        $decor = [];
        if (! empty($styles['underline'])) {
            $decor[] = 'underline';
        }
        if (! empty($styles['strike'])) {
            $decor[] = 'line-through';
        }
        if ($decor !== []) {
            $css[] = 'text-decoration:'.implode(' ', $decor);
        }

        $class = ! empty($styles['obfuscated']) ? ' class="prefix-obfuscated"' : '';
        $style = $css !== [] ? ' style="'.implode(';', $css).'"' : '';

        return '<span'.$class.$style.'>'.$escaped.'</span>';
    }

    private function resolveColorToken(string $token): string
    {
        $token = ltrim($token, '#');
        if (isset(self::NAMED[$token])) {
            return self::NAMED[$token];
        }
        if (preg_match('/^[0-9a-f]{6}$/', $token) === 1) {
            return '#'.$token;
        }

        return '#FFFFFF';
    }
}
