# Lob asset management

A solution created to generate investments income tax release 

--------------
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

##### Operation Types
```Clojure
:sell ;=> (and (= type"Debito")
      ;        (or (= movement-type "Transferência - Liquidação")
      ;            (= movement-type "COMPRA / VENDA")))
:buy  ;=> (and (= type "Credito")
      ;        (or (= movement-type "Transferência - Liquidação")
      ;            (= movement-type "COMPRA / VENDA")))
:desdobro ; Multiply the asset quantity
:grupamento ; Group the asset quantity
:waste ;Fraction of the asset was sold
:dividend
:JCP
:income ;Dividend from Reit assets
:bonificaçãoemativos ;Bonus in asset, increase the asset quantity
:incorporation ;Come from incorporation_movement file in source folder
;;;;IGNORED
:fraçãoemativos ;IGNORED [No considerable values]
:compra ;IGNORED [Government Bound]
:transferência  ;IGNORED
:solicitaçãodesubscrição ;IGNORED
:cessãodedireitos-solicitada  ;IGNORED
:direitodesubscrição  ;IGNORED
:cessãodedireitos ;IGNORED
:direitosdesubscrição-excercído ;IGNORED
:direitosdesubscrição-nãoexercido ;IGNORED
:recibodesubscrição ;IGNORED
:compraporliquides  ;IGNORED
:vencimento ;IGNORED
:resgate  ;IGNORED
:incorporação ;IGNORED
:atualização  ;IGNORED
```

##### Incorporation event

Incorporation events needs to be registered in the file `resources/incorporation_movement` fallowing this format :

```Clojure
[{:transaction-date "21/06/2022" ;Date of the incorporation
  :unit-price       "0,00"       ;Will be all position value when processed
  :quantity         0            ;Will be all position value when processed
  :exchange         "INTER DISTRIBUIDORA DE TITULOS E VALORES MOBILIARIOS LTDA"
  :product          "BIDI11 - BANCO INTER S/A" ;The acquired company ticket
  :operation-total  "0,00"       ;Will be all position value when processed
  :currency         "BRL"        ;BRL or USD
  :movement-type    "Incorporation" ;Turn in transaction/operation-type 
  :incorporated-by  "INBR32"     ;The new asset owner
  :factor           "/2"         ;operator can be "*" or "/" and fallowing by the denominator 
  }]
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
### How to Add new Asset

#### Crypto

1. Add the ticket in the `lob-asset-management.models.asset/crypto-list` 
2. Include the asset in the `resources/asset_fixed_info.edn`
3. Include the corresponding asset id for get price into `lob-asset-management.adapter.asset/in-ticket->out-crypto-id`

--------------
### Portfolio Balance [TODO]

1. Throw Asset type (StockBR StockEUA Crypto) by configuration
   1. Current situation gain/loss focusing in the lowest profit (Configuration first)
2. Throw Categories allocation
   1. To compare current allocation percentage with configured allocation
3. Throw Category assets balance allocation 
   1. Maintain close the asset distribution
   2. Verify the current gain/loss focusing in assets with loss
   3. Identify the best asset (Classify assets) / Determined asset configured allocation (throw category)

**Rules**

- Different configuration throw types (StockBR - StockEUA - Crypto)
  - StockBR we use quantity and StockEUA/Crypto we use total money to allocate
  - StockBR => Buy at least one asset quantity
  - StockEUA/Crypto => Distribute the amount based in the category allocation and asset priority
- Configure asset allowed to be purchased
- Configure asset priority
  - options :
    - [ ] Fixed Percentage (ex 50)
    - [ ] Classification range number (ex 1~5)
    - [ ] Recent or/and total decrease in price have most priority
    - [ ] Composition of recent/total decrease in value plus classification

**Flow StockBR**
1. Determine how much money will be allocated
2. Buy at least one position for each allowed asset
3. Current category allocation with the previews step X category configuration
4. Divide the rest of the money following the reminder needs to archive the configuration by category
5. Follow the asset priority configuration to choose the asset

**Flow Stock EUA**
1. Determine how much money will be allocated
2. Check the current category allocation x configuration
3. Divide the total between category to archive the goal
4. Divide the total in category by asset fallowing asset priority configuration

**Flow for Crypto**
1. Determine how much money will be allocated
2. Divide the total between asset priority

--------------
## How allow Telegram Bot

1. [Obtain Your Bot Token](https://core.telegram.org/bots/tutorial#obtain-your-bot-token)
2. Set the key as `telegram-bot-key` environment variable

--------------
## TODO

1. Provide where asset need to be purchase and how much `high
   1. [X] v1 Get allocation needs following configuration 
   2. [ ] v2 Consider extra budget
   3. [ ] v3 Consider recent asset performance
   4. [ ] vX Simulator
2. Store data `Medium`
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
   4. XTDB 
3. [Http_in] Find another option for Alpha API / Web Scraping `Medium`
   1. [X] Coingecko for crypto 
   2. [ ] Web Scraping for Stock information
4. Consider subscription events `Low`
5. Get asset information `Very low`
6. Decision between stock APIs
   1. [ ] Validate if the historic price is updated
   2. [ ] Get more often with real time prices API
   3. [ ] When error in one get using another
      1. Create a new ident for error (E.g :asset/recent-alpha-api-error? true)
      2. If true => Use another
8. Fix the telegram scheduled messages [CRON]
9. Make the poling async and different by proposal (Get price / Get news / TelegramBot)


## FIXME
1. Consider ALZR subscription

--------------
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
