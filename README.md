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

### Portfolio information

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

1. [Release IRPF] Create USD/BRL price list file getting information from ALPHA_API
2. [Release IRPF] Generate file 
3. Turn solution on API or CronJob
4. Store data on cloud
   1. [] AWS
   2. [] Firebase
   3. [] Other
5. Add last price to portfolio
6. Calculate current profit/loss market price based
7. Register Alpha API ticket in asset/fixed-info ?
8. Add a way to manual insert information (Front-end proj?)
9. Update portfolio after get market last price
10. 

## FIXME
1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)
2. Consider desdobramento in the portfolio process (OIBR3)
3. Adjust category for govern bounds and private bounds
4. Portfolio percentage with zero
5. CDB transaction are with operation-type = compra/venda

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
