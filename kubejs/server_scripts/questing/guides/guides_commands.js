// priority: 500
//
// Debug commands for the guide system (see guides_engine.js). All require permission level 2.
//
//   /guides list                 every guide and whether it is unlocked
//   /guides info <id>            triggers and state of one guide
//   /guides unlock <id>          unlock for real: popup book for everyone + pause
//   /guides grant <id|all>       unlock silently (no popup, no pause)
//   /guides lock <id|all>        relock, so a trigger can be tested again
//   /guides popup <id>           run the popup + pause flow for everyone WITHOUT changing unlock state
//   /guides show <id>            preview one page on your own screen only (no pause)
//   /guides books                list the books and how many pages each has
//   /guides give <book> [player] give a guide book item for that book (default: yourself)
//   /guides pages                list the lost pages
//   /guides page <page> [player] give a lost page item (default: yourself)
//   /guides book [<book>]        open a book on your screen (default: the first book)
//   /guides bookall <book>       same, but with locked pages too, marked [locked]
//   /guides probe                what the sweeps see for you right now, and whether each locked sweep trigger passes
//   /guides trace                toggle live debug output for you: sweep state on the action bar, every event in chat
//   /guides fire <name>          fire a custom event as yourself
//   /guides status               popup / pause / validation state
//   /guides reload               re-read guides.json now (it is also re-read every 2 seconds)
//   /guides resume               emergency: drop the popup queue and release the pause we hold

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event
    const G = () => global.Guides

    function feedback(source, message) {
        if (source.player) source.player.tell(message)
        else source.server.tell(message)
    }

    function idArg(withAll) {
        return Commands.argument('id', Arguments.WORD.create(event))
            .suggests((ctx, builder) => {
                if (withAll) builder.suggest('all')
                G().ids().forEach(id => builder.suggest(id))
                return builder.buildFuture()
            })
    }

    function bookArg() {
        return Commands.argument('book', Arguments.WORD.create(event))
            .suggests((ctx, builder) => {
                G().bookIds().forEach(id => builder.suggest(id))
                return builder.buildFuture()
            })
    }

    // the book named in the command, or null (after telling the user)
    function bookFrom(ctx) {
        let id = Arguments.WORD.getResult(ctx, 'book')
        let book = G().findBook(id)
        if (!book) feedback(ctx.source, Text.red(`[Guides] Unknown book '${id}' (try /guides books)`))
        return book
    }

    function pageFrom(ctx) {
        let id = Arguments.WORD.getResult(ctx, 'page')
        let page = G().findLostPage(id)
        if (!page) feedback(ctx.source, Text.red(`[Guides] Unknown lost page '${id}' (try /guides pages)`))
        return page
    }

    // Runs fn(guide) for one id, or for every guide when withAll and id is 'all'. Returns the command result.
    function withGuides(ctx, withAll, fn) {
        let id = Arguments.WORD.getResult(ctx, 'id')
        let targets = withAll && id === 'all' ? G().list() : [G().find(id)]
        if (!targets[0]) {
            feedback(ctx.source, Text.red(`[Guides] Unknown guide '${id}'`))
            return 0
        }
        let count = 0
        targets.forEach(g => { if (fn(g)) count++ })
        return count
    }

    function needPlayer(ctx) {
        if (ctx.source.player) return ctx.source.player
        feedback(ctx.source, Text.red('[Guides] This command must be run by a player'))
        return null
    }

    function specToString(spec) {
        if (spec === undefined) return '*'
        if (Array.isArray(spec)) return spec.map(specToString).join(', ')
        if (typeof spec === 'function') return '<function>'
        return String(spec)
    }

    function describeTrigger(t) {
        if (!t) return '<invalid>'
        let kind = t.sweep || t.event
        let parts = []
        if (t.sweep === 'inventory') {
            parts.push(`item=${specToString(t.item)}`)
            if (t.count !== undefined) parts.push(`count>=${t.count}`)
        } else if (t.sweep !== 'custom') {
            parts.push(`match=${specToString(t.match)}`)
        }
        if (t.distance !== undefined) parts.push(`distance<=${t.distance}`)
        if (t.by !== undefined) parts.push(`by=${specToString(t.by)}`)
        if (t.minDamage !== undefined) parts.push(`minDamage=${t.minDamage}`)
        if (t.where) parts.push('where=<function>')
        return `${t.sweep ? 'sweep' : 'event'} ${kind} ${parts.join(' ')}`.trim()
    }

    function stateText(unlocked) {
        return unlocked ? Text.green('unlocked') : Text.red('locked')
    }

    event.register(
        Commands.literal('guides')
            .requires(source => source.hasPermission(2))

            .then(Commands.literal('list')
                .executes(ctx => {
                    let server = ctx.source.server
                    feedback(ctx.source, Text.gray('--- [ Guides ] ---'))
                    G().list().forEach((g, i) => {
                        feedback(ctx.source, Text.gray(`${i + 1}. `).append(Text.white(`${g.id} `)).append(Text.gray(`[${g.book}] `)).append(stateText(G().isUnlocked(server, g.id))))
                    })
                    return 1
                })
            )

            .then(Commands.literal('info')
                .then(idArg(false).executes(ctx => withGuides(ctx, false, g => {
                    let server = ctx.source.server
                    feedback(ctx.source, Text.gold(`${g.id} `).append(Text.gray(`"${g.title}" in book ${g.book} `)).append(stateText(G().isUnlocked(server, g.id))))
                    let triggers = Array.isArray(g.unlock) ? g.unlock : (g.unlock ? [g.unlock] : [])
                    if (triggers.length === 0) feedback(ctx.source, Text.gray('  (no triggers: commands/code only)'))
                    triggers.forEach(t => feedback(ctx.source, Text.gray('  any of: ').append(Text.yellow(describeTrigger(t)))))
                    return true
                })))
            )

            .then(Commands.literal('unlock')
                .then(idArg(false).executes(ctx => withGuides(ctx, false, g => {
                    let ok = G().unlock(ctx.source.server, g.id, { player: ctx.source.player })
                    feedback(ctx.source, ok ? Text.green(`[Guides] Unlocked ${g.id}`) : Text.yellow(`[Guides] ${g.id} was already unlocked (use /guides popup ${g.id} to replay it)`))
                    return ok
                })))
            )

            .then(Commands.literal('grant')
                .then(idArg(true).executes(ctx => {
                    let n = withGuides(ctx, true, g => G().unlock(ctx.source.server, g.id, { silent: true }))
                    feedback(ctx.source, Text.green(`[Guides] Silently unlocked ${n} guide(s)`))
                    return n
                }))
            )

            .then(Commands.literal('lock')
                .then(idArg(true).executes(ctx => {
                    let n = withGuides(ctx, true, g => G().lock(ctx.source.server, g.id))
                    feedback(ctx.source, Text.green(`[Guides] Locked ${n} guide(s)`))
                    return n
                }))
            )

            .then(Commands.literal('popup')
                .then(idArg(false).executes(ctx => withGuides(ctx, false, g => {
                    G().popup(ctx.source.server, g.id)
                    return true
                })))
            )

            .then(Commands.literal('show')
                .then(idArg(false).executes(ctx => {
                    let player = needPlayer(ctx)
                    if (!player) return 0
                    return withGuides(ctx, false, g => {
                        G().openPages(player, [G().renderPage(g, false)])
                        return true
                    })
                }))
            )

            .then(Commands.literal('books')
                .executes(ctx => {
                    let server = ctx.source.server
                    feedback(ctx.source, Text.gray('--- [ Books ] ---'))
                    G().books().forEach(b => {
                        let pages = G().list().filter(g => g.book === b.id)
                        let open = pages.filter(g => G().isUnlocked(server, g.id)).length
                        feedback(ctx.source, Text.white(`${b.id} `).append(Text.gray(`"${b.name}" ${open}/${pages.length} pages unlocked`)))
                    })
                    return 1
                })
            )

            .then(Commands.literal('give')
                .then(bookArg()
                    .executes(ctx => {
                        let player = needPlayer(ctx)
                        let book = player && bookFrom(ctx)
                        if (!book) return 0
                        player.give(G().makeBookItem(book))
                        feedback(ctx.source, Text.green(`[Guides] Gave ${book.name}`))
                        return 1
                    })
                    .then(Commands.argument('player', Arguments.PLAYER.create(event))
                        .executes(ctx => {
                            let book = bookFrom(ctx)
                            if (!book) return 0
                            let target = Arguments.PLAYER.getResult(ctx, 'player')
                            target.give(G().makeBookItem(book))
                            feedback(ctx.source, Text.green(`[Guides] Gave ${book.name} to ${target.username}`))
                            return 1
                        })
                    )
                )
            )

            .then(Commands.literal('pages')
                .executes(ctx => {
                    feedback(ctx.source, Text.gray('--- [ Lost pages ] ---'))
                    G().lostPages().forEach(p => feedback(ctx.source, Text.white(`${p.id} `).append(Text.gray(`"${p.name}"`))))
                    return 1
                })
            )

            .then(Commands.literal('page')
                .then(Commands.argument('page', Arguments.WORD.create(event))
                    .suggests((ctx, builder) => {
                        G().lostPages().forEach(p => builder.suggest(p.id))
                        return builder.buildFuture()
                    })
                    .executes(ctx => {
                        let player = needPlayer(ctx)
                        let page = player && pageFrom(ctx)
                        if (!page) return 0
                        player.give(G().makeLostPageItem(page))
                        feedback(ctx.source, Text.green(`[Guides] Gave ${page.name}`))
                        return 1
                    })
                    .then(Commands.argument('player', Arguments.PLAYER.create(event))
                        .executes(ctx => {
                            let page = pageFrom(ctx)
                            if (!page) return 0
                            let target = Arguments.PLAYER.getResult(ctx, 'player')
                            target.give(G().makeLostPageItem(page))
                            feedback(ctx.source, Text.green(`[Guides] Gave ${page.name} to ${target.username}`))
                            return 1
                        })
                    )
                )
            )

            .then(Commands.literal('book')
                .executes(ctx => {
                    let player = needPlayer(ctx)
                    if (!player) return 0
                    G().openFullBook(player, null, false, null)
                    return 1
                })
                .then(bookArg().executes(ctx => {
                    let player = needPlayer(ctx)
                    let book = player && bookFrom(ctx)
                    if (!book) return 0
                    G().openFullBook(player, null, false, book.id)
                    return 1
                }))
            )

            .then(Commands.literal('bookall')
                .then(bookArg().executes(ctx => {
                    let player = needPlayer(ctx)
                    let book = player && bookFrom(ctx)
                    if (!book) return 0
                    G().openFullBook(player, null, true, book.id)
                    return 1
                }))
            )

            .then(Commands.literal('probe')
                .executes(ctx => {
                    let player = needPlayer(ctx)
                    if (!player) return 0
                    let server = ctx.source.server
                    let sweepCtx = G().makeSweepCtx(player)
                    let s = G().sweepSnapshot(sweepCtx)

                    feedback(ctx.source, Text.gray('--- [ Sweep probe ] ---'))
                    feedback(ctx.source, Text.aqua(`standing on: ${s.standingOn}`))
                    feedback(ctx.source, Text.aqua(`standing in: ${s.standingIn}`))
                    feedback(ctx.source, Text.aqua(`looking at block: ${s.block}`))
                    feedback(ctx.source, Text.aqua(`looking at entity: ${s.entity}`))
                    feedback(ctx.source, Text.aqua(`distance: ${s.distance}`))
                    feedback(ctx.source, Text.gray(`ray: type=${s.rayType} hit=(${s.rayHit}) kubejs-distance-field=${s.rayReportedDistance} (this is the max reach, not the hit distance)`))
                    console.info(`[Guides] probe ${player.username}: ${JSON.stringify(s)}`)

                    G().list().forEach(g => {
                        let unlocked = G().isUnlocked(server, g.id)
                        let triggers = Array.isArray(g.unlock) ? g.unlock : (g.unlock ? [g.unlock] : [])
                        triggers.forEach(t => {
                            if (!t || !t.sweep) return
                            let pass
                            try { pass = G().evalSweep(t, sweepCtx) } catch (e) { pass = 'error: ' + e }
                            let shown = pass === true ? Text.green('PASS') : (pass === false ? Text.red('fail') : Text.red(String(pass)))
                            feedback(ctx.source, Text.gray(`${g.id}${unlocked ? ' (unlocked)' : ''}: ${describeTrigger(t)} -> `).append(shown))
                        })
                    })
                    return 1
                })
            )

            .then(Commands.literal('trace')
                .executes(ctx => {
                    let player = needPlayer(ctx)
                    if (!player) return 0
                    let key = String(player.uuid)
                    let rt = G().runtime
                    if (rt.trace[key]) delete rt.trace[key]
                    else rt.trace[key] = true
                    feedback(ctx.source, Text.gray('[Guides] Trace ').append(rt.trace[key] ? Text.green('ON') : Text.red('OFF')))
                    return 1
                })
            )

            .then(Commands.literal('fire')
                .then(Commands.argument('name', Arguments.WORD.create(event))
                    .executes(ctx => {
                        let player = needPlayer(ctx)
                        if (!player) return 0
                        G().fire(player, Arguments.WORD.getResult(ctx, 'name'), {})
                        return 1
                    })
                )
            )

            .then(Commands.literal('status')
                .executes(ctx => {
                    let server = ctx.source.server
                    let rt = G().runtime
                    let forced = G().readForcePause()
                    let unlocked = G().unlockedIds(server).length
                    let problems = (G().runtime.dataProblems || []).concat(G().validate())

                    feedback(ctx.source, Text.gray('--- [ Guide status ] ---'))
                    feedback(ctx.source, Text.aqua(`Unlocked: ${unlocked}/${G().list().length}`))
                    feedback(ctx.source, Text.aqua(`Popup active: ${rt.popupActive} | viewers: ${G().viewerCount()} | queued: ${rt.queue.length}`))
                    feedback(ctx.source, Text.aqua(`Force pause: ${forced === null ? 'unknown' : forced} | pause held by guides: ${rt.ownsPause}`))
                    feedback(ctx.source, Text.aqua(`Sweep interval: ${G().config.SWEEP_INTERVAL_TICKS} ticks`))
                    if (problems.length === 0) feedback(ctx.source, Text.green('Guide definitions: OK'))
                    problems.forEach(p => feedback(ctx.source, p.indexOf('warning: ') === 0 ? Text.yellow(p) : Text.red(p)))
                    return 1
                })
            )

            .then(Commands.literal('reload')
                .executes(ctx => {
                    let ok = G().reload(null)
                    feedback(ctx.source, ok ? Text.green('[Guides] guides.json reloaded') : Text.red('[Guides] guides.json rejected, see the server log or /guides status'))
                    return ok ? 1 : 0
                })
            )

            .then(Commands.literal('resume')
                .executes(ctx => {
                    G().reset(ctx.source.server)
                    feedback(ctx.source, Text.green('[Guides] Popup state cleared, pause released'))
                    return 1
                })
            )
    )
})
