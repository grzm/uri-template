# uri-template - Clojure URI Template processor

uri-template is a Clojure implementation of a template processor
following the specification described in [URI Template (RFC
6570)][RFC6570].

It's compliant with templates up through Level 4 (i.e., all specified
levels), and passes all tests provided in the [uritemplate-test
repo][uritemplate-test]

[RFC6570]: https://www.rfc-editor.org/rfc/rfc6570
[uritemplate-test]: https://github.com/uri-templates/uritemplate-test

## Release information

``` clojure
com.grzm/uri-template {:mvn/version "0.7.1"}
```
## Usage

The library provides a single function:
`com.grzm.uri-template/expand`. The `expand` function takes two
arguments: the URI template, and a map specifying variable bindings.

```clojure
(require '[com.grzm.uri-template :as ut])
```

Here we expand the template `"https://example.com/~{username}"` and
substitute the string `"fred"` for the variable `username`.

```clojure
(ut/expand "https://example.com/~{username}" {"username" "fred"})
;; => "https://example.com/~fred"
```

If the template can't be parsed, `expand` will return an
[cognitect.anomalies][] map describing the error. In the following
example, the variable expression is missing the closing `"}"`.

```clojure
(ut/expand "https://example.com/~{username" {"username" "fred"})
;; => {:cognitect.anomalies/category :cognitect.anomalies/incorrect, :error :early-termination, :idx 30, :template "https://example.com/~{username"}
```

[cognitect.anomalies]: https://github.com/cognitect-labs/anomalies

### URI Template syntax

The URI Template processor treats the URI Template as a string: it
does not inspect its form or validate it as a URI. Some of the
examples take advantage of this to isolate an expression to highlight
expansion behavior. See the [RFC][RFC6570] for the definitive (if
terse) explanation.

```clojure
(ut/expand "{var}" {"var" "val"})
;; => "val"

(ut/expand "{half}" {"var" "val", "half" "50%", "two.bits" "25%25"})
;; => "50%25"
```

```clojure
(ut/expand "{+var}" {"var" "val"})
;; => "val"

(ut/expand "{+half}" {"var" "val", "half" "50%"})
;; => "50%25"

```

Hyphens are not valid variable name characters, so the Clojurist's
preferred kebab-style naming is disallowed. Underscores and dots are
permitted, and varnames respect case.

```clojure
(ut/expand "{the-var}" {"the-var" "some-val"})
;; => {:cognitect.anomalies/category :cognitect.anomalies/incorrect, :cognitect.anomalies/message "Invalid varname character.", :error :unrecognized-character, :character "-", :idx 5, :template "{the-var}"}

(ut/expand "{TheVar,the.var,theVar,the_var}",
           {"the_var" "bat",
            "theVar", "baz",
            "TheVar" "foo",
            "the.var", "bar"})
;; => "foo,bar,baz,bat"
```

Variables in templates that have no binding are dropped. Similarly,
bindings that aren't included in the template are ignored.

```clojure
(ut/expand "{apple,pear}" {"apple" "red", "lime" "green"})
;; => "red"
```

URI Template provides a number of operators that provide different
variable expansion behaviors. In the examples, we'll use the following
variable binding:

```clojure
(def vars {"var" "some-value"
           "half" "50%"
           "hello" "Hello World!"
           "list" ["foo" "bar" "baz"]})
```

#### Simple variable expansion

Simple variable expansion is used when variables in the template are
included as-is. Only characters that require percent encoding are so
encoded.

```clojure
(ut/expand "https://example.com/some/path/{var}" vars)
;; => "https://example.com/some/path/some-value"

(ut/expand "https://example.com/some/path/{half}" vars)
;; => "https://example.com/some/path/50%25"

(ut/expand "https://example.com/some/path/{hello}" vars)
;; => "https://example.com/some/path/Hello%20World%21"

(ut/expand "https://example.com/some/path/{list}" vars)
;; => "https://example.com/some/path/foo,bar,baz"
```

#### `+` Reserved expansion

Variable names prefixed with `+` are expanded according to the rules
of _reserved expansion_. Any reserved character is percent encoded.

```clojure
(ut/expand "https://example.com/some/path/{+var}" vars)
;; => "https://example.com/some/path/some-value"

(ut/expand "https://example.com/some/path/{+half}" vars)
;; => "https://example.com/some/path/50%25"

(ut/expand "https://example.com/some/path/{+hello}" vars)
;; => "https://example.com/some/path/Hello%20World!"
```

#### `?` Query expansion and `#` Fragment expansion

_Query expansion_ (with a `?` prefix) and _fragment expansion_ (with a
`#` prefix) are used for query parameters and fragments.

Note that the variable `special` is not defined.

```clojure
(ut/expand "https://example.com/some/path{?var,special}" vars)
;; => "https://example.com/some/path?var=some-value"

(ut/expand "https://example.com/some/path{#var}" vars)
;; => "https://example.com/some/path#some-value"

(ut/expand "https://example.com/some/path{?list}" vars)
;; => "https://example.com/some/path?list=foo,bar,baz"

(ut/expand "https://example.com/some/path{#special}" vars)
;; => "https://example.com/some/path"
```
See the [RFC][RFC6570] for a complete description of operators and their behaviors.

#### Patterns not supported by URI Template

Some common methods of encoding array query parameter values aren't
supported by URI Template. For example, there isn't a way to represent
the following patterns to represent a variable `list` with a value
`["foo" "bar" "baz"]`.

* `https://example.com/?list[]=foo&list[]=bar&list[]=baz`
* `https://example.com/?list[1]=foo&list[2]=bar&list[3]=baz`

A query parameter with multiple values is represented as the following:

```clojure
(ut/expand "https://example.com{?list*}" vars)
;; => "https://example.com?list=foo&list=bar&list=baz"
```

## Variable binding

### Variable binding and types

Section 2.4.2 of the RFC explains

>    Since URI Templates do not contain an indication of type or schema,
>    the type for an exploded variable is assumed to be determined by
>    context.  For example, the processor might be supplied values in a
>    form that differentiates values as strings, lists, or associative
>    arrays.  Likewise, the context in which the template is used (script,
>    mark-up language, Interface Definition Language, etc.) might define
>    rules for associating variable names with types, structures, or
>    schema.

For the context of this implementation, any Clojure value that
implements `IPersistentCollection` is considered a composite value:
any other value is coerced to `String` and treated as a scalar.

Any composite value that implements `IPersistentMap` is treated as an
associative array, and is otherwise treated as a list. In code:

``` clojure
(if (coll? value)
  (if (map? value)
    :associative-array
    :list)
  :string)
```

### Special values

Boolean values (`true`, `false`), like all non-composite values, are
coerced to strings. For the purposes of expansion, the following
variable maps are equivalent:

```clojure
 {"truth" true}
 {"truth" "true"}
```

The value `nil` is considered undefined for the purposes of the RFC,
and undefined values are omitted, just as if they were missing. This
includes list and map values. The following variable maps are
equivalent for the purposes of template expansion:

```clojure
{"list" ["a" nil "b"]
 "keys" {"missing" nil, "bar" "baz"}
 "empty_list" [],
 "empty_keys" {},
 "empty" nil}
{"list" ["a" "b"]
 "keys" {"bar" "baz"}}
```
### Variable map keys

Variable assignments are a Clojure map passed as the second argument
to `expand`. It's common to use keywords as map keys in Clojure, and
keyword keys are accepted in variable maps, both as variable names and
as keys of the corresponding variable values. URI Template variable
names can contain character sequences that are invalid as Clojure
keywords. The `expand` function accepts variable maps with keys that
are keywords or strings: keyword keys are coerced to strings using
`name`.

The behavior of `expand` when provided with a variable map with
multiple keys that coerce to the same string is undefined.

## Design goals

* No dependencies
* Babashka-compatible
* Helpful template syntax error messages
* Maintainability

### Non-goals
* Performance. It should be fast enough to be useful and not get in
  the way. Pursuit of performance should not be to the detriment of
  the design goals.

## Development testing

```bash
# run the unit tests
clojure -M:test:kaocha :unit

# run the property-based tests, excluding the exceptionally slow ones
clojure -M:test:kaocha :gen

# run those exceptionally slow ones
clojure -M:test:kaocha :slow
```

We can test the examples in this README as well using Sean Corfield's
nifty [readme][seancorfield.readme] library.

[seancorfield.readme]: https://github.com/seancorfield/readme

```bash
clojure -M:readme
```

If you're working on the README examples and happen to have [entr][]
installed, you can get `watch`-like behavior with

```bash
echo README.markdown | entr clojure -M:readme
```

[entr]: http://eradman.com/entrproject/

## Similar Clojure libraries
* https://github.com/dfa1/uritemplate
* https://github.com/mwkuster/uritemplate-clj

Davide Angelocola's
[dfa1/uritemplate](https://github.com/dfa1/uritemplate) in particular
was useful for looking at a concrete interpretation of the RFC.

# License and Copyright
© 2021–2022 Michael Glaesemann

Released under the MIT License. See LICENSE file for details.
