# Lob asset management

A solution created to generate investments income tax release 

## Usage
### Read XLSX movements

Read a XLSX document and turn in EDN entities:

 - Asset
 - Transaction
 - Portfolio

#### Asset

Register all unique `:asset/ticket` from movements and use `resources/asset_fixed_info.edn` to complement information

```Clojure 
:asset/ticket 
:asset/tax-number
:asset/id
:asset.market-price/price
:asset.market-price/price-date
:asset/category
:asset.market-price/updated-at
:asset.market-price/historic 
:asset/name
:asset/type
```

#### Transactions

Financial movements, convert all movements from XLSX documents to EDN

```Clojure 
:transaction/operation-type
:transaction/operation-total
:transaction/currency
:transaction/average-price
:transaction.asset/ticket
:transaction/id
:transaction/processed-at
:transaction/quantity
:transaction/exchange
:transaction/created-at
```

#### Portfolio

The consolidation of transactions grouped by `:transaction.asset/ticket`

```Clojure 
:portfolio/transaction-ids
:portfolio/quantity
:portfolio/percentage
:portfolio/sell-profit
:portfolio/total-cost
:portfolio/dividend
:portfolio/total-last-value
:portfolio/category
:portfolio/exchanges
:portfolio/average-price
:portfolio/ticket
:portfolio.profit-loss/percentage 
:portfolio.profit-loss/value
```
### Portfolio Configuration [TODO]

1. Throw Asset type (StockBR StockEUA Crypto) by configuration
   1. Current situation gain/loss focusing in the lowest profit (Configuration first)
2. Throw Categories allocation
   1. To compare current allocation percentage with configured allocation
3. Throw Category assets balance allocation 
   1. Maintain close the asset distribution
   2. Verify the current gain/loss focusing in assets with loss
   3. Identify the best asset (Classify assets) / Determined asset configured allocation (throw category)

## How allow Telegram Bot

1. [Obtain Your Bot Token](https://core.telegram.org/bots/tutorial#obtain-your-bot-token)
2. Set the key as `telegram-bot-key` environment variable

Provide where asset need to be purchase and how much

## TODO

1. Store data on cloud
   1. [ ] AWS
      1. ~~Thinking about the item 4. maybe we can use S3 only for the in-data files~~
   2. [ ] Firebase (Doing)
      1. [x] Find firebase library (alekcz/fire)
      2. [x] Create firebase project 
      3. [ ] Fix connection only works in terminal (lein run)
      4. [ ] Create write/read documents
   3. [ ] Do nothing
      1. Solution works for read/write entities in terminal
      2. ~~The problem is only **maybe** in the in-data files~~
2. Add a way to manual insert information (Front-end proj?)
   1. Function to read stored file and add one row
3. [Documentation] Document the transaction types and what happening
4. [Http_in] Find another option for Alpha API / Web Scraping
5. [Market] Count API calls
6. Consider subscription events

## FIXME

1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)

   - Two new Event [Movement] (:incorporation-sell / :incorporation)
   
   - Create new entity `global event`
   ```Clojure
   [{:transaction-date  "21/06/2022"
     :unit-price        "0,00"      ;All position value
     :quantity          0           ;All position value
     :exchange          "INTER DISTRIBUIDORA DE TITULOS E VALORES MOBILIARIOS LTDA"
     :product           "BIDI11 - BANCO INTER S/A"
     :operation-total   "0,00"      ;All position value
     :currency          "BRL"
     :movement-type     "Incorporação venda"}
    {:transaction-date  "21/06/2022"
     :unit-price        "44,64"
     :quantity          4
     :exchange          "INTER DISTRIBUIDORA DE TITULOS E VALORES MOBILIARIOS LTDA"
     :product           "INBR32 - INTER CO INC"
     :operation-total   "178,57"
     :currency          "BRL"
     :movement-type     "Incorporação"}]
   ```
2. Set telegram key as environment variable

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
