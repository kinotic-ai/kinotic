Copyright (c) 2026 Minds Ignited LLC. All rights reserved.

Portions of this software are licensed as follows:

- All content that resides in the [`kinotic-ee/`](kinotic-ee) directory of this repository is
  licensed under the Kinotic Enterprise License defined in
  [`kinotic-ee/LICENSE`](kinotic-ee/LICENSE).

- All content that resides in the [`kinotic-core/`](kinotic-core),
  [`kinotic-api-gateway/`](kinotic-api-gateway), [`kinotic-client/`](kinotic-client),
  [`kinotic-idl/`](kinotic-idl),
  [`kinotic-js/workspace/packages/core/`](kinotic-js/workspace/packages/core) and
  [`kinotic-js/workspace/packages/idl/`](kinotic-js/workspace/packages/idl) directories of this
  repository (Kinotic RPC) is licensed under the Apache License 2.0 defined in
  [`LICENSE-APACHE`](LICENSE-APACHE).

- All third-party components incorporated into the Kinotic software are licensed under the
  original license provided by the owner of the applicable component.

- Content outside of the above-mentioned directories is available under the "Elastic License 2.0"
  as defined below, subject to the Kinotic Additional Limitations that precede it.

# Kinotic Additional Limitations

The licensor grants the Elastic License 2.0 subject to the following limitations, in addition to
the limitations the Elastic License 2.0 itself sets out. These limitations are part of the
"limitations and conditions" to which the Elastic License 2.0 grant is subject, so a use that
violates them is not licensed. The terms "Development and Testing", "Enterprise Feature", and
"Production Use" have the meanings given in the Kinotic Enterprise License.

## Enterprise Extension Points

Any interface, abstract class, or annotation in a Java package that has a segment named `ee`
(for example `org.kinotic.domain.api.ee.services`), or in a directory named `ee`, outside the
`kinotic-ee/` directory, is an Enterprise Extension Point. The capability an Enterprise
Extension Point provides is an Enterprise Feature, licensed only under the Kinotic Enterprise
License. A type is an Enterprise Extension Point in every published version of the software that
places it in such a package or directory.

## Substitute implementations

Except as a written agreement with the licensor expressly permits, you may not, for Production
Use:

1. implement or extend an Enterprise Extension Point, or use such an implementation, other than
   one the licensor provides in `kinotic-ee/` or includes in the software under the Elastic
   License 2.0 and that you use unmodified;
2. modify the software to move a type out of such a package or directory, or to replace, bypass,
   or wrap an Enterprise Extension Point or the code that uses it, so that its capability is
   provided by other software; or
3. otherwise provide the capability of an Enterprise Extension Point in a manner designed to
   avoid the need for a license to the corresponding Enterprise Feature.

You may implement an Enterprise Extension Point, and modify the software, for your own
Development and Testing.

# Elastic License 2.0

URL: https://www.elastic.co/licensing/elastic-license

## Acceptance

By using the software, you agree to all of the terms and conditions below.

## Copyright License

The licensor grants you a non-exclusive, royalty-free, worldwide,
non-sublicensable, non-transferable license to use, copy, distribute, make
available, and prepare derivative works of the software, in each case subject to
the limitations and conditions below.

## Limitations

You may not provide the software to third parties as a hosted or managed
service, where the service provides users with access to any substantial set of
the features or functionality of the software.

You may not move, change, disable, or circumvent the license key functionality
in the software, and you may not remove or obscure any functionality in the
software that is protected by the license key.

You may not alter, remove, or obscure any licensing, copyright, or other notices
of the licensor in the software. Any use of the licensor’s trademarks is subject
to applicable law.

## Patents

The licensor grants you a license, under any patent claims the licensor can
license, or becomes able to license, to make, have made, use, sell, offer for
sale, import and have imported the software, in each case subject to the
limitations and conditions in this license. This license does not cover any
patent claims that you cause to be infringed by modifications or additions to
the software. If you or your company make any written claim that the software
infringes or contributes to infringement of any patent, your patent license for
the software granted under these terms ends immediately. If your company makes
such a claim, your patent license ends immediately for work on behalf of your
company.

## Notices

You must ensure that anyone who gets a copy of any part of the software from you
also gets a copy of these terms.

If you modify the software, you must include in any modified copies of the
software prominent notices stating that you have modified the software.

## No Other Rights

These terms do not imply any licenses other than those expressly granted in
these terms.

## Termination

If you use the software in violation of these terms, such use is not licensed,
and your licenses will automatically terminate. If the licensor provides you
with a notice of your violation, and you cease all violation of this license no
later than 30 days after you receive that notice, your licenses will be
reinstated retroactively. However, if you violate these terms after such
reinstatement, any additional violation of these terms will cause your licenses
to terminate automatically and permanently.

## No Liability

*As far as the law allows, the software comes as is, without any warranty or
condition, and the licensor will not be liable to you for any damages arising
out of these terms or the use or nature of the software, under any kind of
legal claim.*

## Definitions

The **licensor** is the entity offering these terms, and the **software** is the
software the licensor makes available under these terms, including any portion
of it.

**you** refers to the individual or entity agreeing to these terms.

**your company** is any legal entity, sole proprietorship, or other kind of
organization that you work for, plus all organizations that have control over,
are under the control of, or are under common control with that
organization. **control** means ownership of substantially all the assets of an
entity, or the power to direct its management and policies by vote, contract, or
otherwise. Control can be direct or indirect.

**your licenses** are all the licenses granted to you for the software under
these terms.

**use** means anything you do with the software requiring one of your licenses.

**trademark** means trademarks, service marks, and similar rights.
