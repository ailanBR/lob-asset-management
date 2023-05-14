# lob-asset-management

A system to provide asset information's to management using functional programming with Clojure.

## Usage

### Import asset movements

Read a XLSX document and turn in EDN fallowing the movement type:

 - Buy
 - Sell
 - Dividend
 - Bonus

(OBS: In the first version only BR stocks)

### Portfolio informations

- Average price
- Total cost
- Profit from dividend's
- Allocation percentage

### Portfolio balance

Provide where asset need to be purchase and how much

### Asset information

- Price
- Profit/Loss
- Portfolio percentage1
- Purchase quantity media

## TODO

1. Read EUA equities information
2. Read crypto information
3. Turn solution on API or CronJob
4. Store data on cloud
   1. [] AWS
   2. [] Firebase
   3. [] Other
5. Register Alpha API ticket in asset ?
6. Update portfolio last price
7. Calculate current profit/loss market price based
8. Add a way to manual insert information (Front-end proj?)
9. Add last price to portfolio
10. Update portfolio after get market last price

## FIXME
1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)
2. Consider desdobramento in the portfolio process (OIBR3)
3. Adjust category for govern bounds and private bounds
4. Portfolio percentage with zero

## License

Copyright © 2023 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
