// Compiled by ClojureScript 1.10.439 {}
goog.provide('cognitect.monaco.paredit');
goog.require('cljs.core');
cognitect.monaco.paredit.rebl_editor = (function cognitect$monaco$paredit$rebl_editor(){
return window.editor;
});
cognitect.monaco.paredit.paredit_ast = (function cognitect$monaco$paredit$paredit_ast(src){
return paredit.parse(src);
});
cognitect.monaco.paredit.offset = (function cognitect$monaco$paredit$offset(model,position){
return model.getOffsetAt(position);
});
cognitect.monaco.paredit.get_selection = (function cognitect$monaco$paredit$get_selection(editor){
var model = editor.getModel();
var sel = editor.getSelection();
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"start","start",-355208981),cognitect.monaco.paredit.offset.call(null,model,sel.getStartPosition()),new cljs.core.Keyword(null,"end","end",-268185958),cognitect.monaco.paredit.offset.call(null,model,sel.getEndPosition()),new cljs.core.Keyword(null,"cur","cur",1153190599),cognitect.monaco.paredit.offset.call(null,model,sel.getPosition())], null);
});
/**
 * takes paredit.js results and applies them to Monaco
 */
cognitect.monaco.paredit.apply_edits = (function cognitect$monaco$paredit$apply_edits(editor,pinfo){
if(cljs.core.truth_(pinfo)){
var model = editor.getModel();
var range = ((function (model){
return (function (start,end){
return monaco.Range.fromPositions(model.getPositionAt(start),model.getPositionAt(end));
});})(model))
;
var chgs = cljs.core.map.call(null,((function (model,range){
return (function (p__3317){
var vec__3318 = p__3317;
var op = cljs.core.nth.call(null,vec__3318,(0),null);
var i = cljs.core.nth.call(null,vec__3318,(1),null);
var arg = cljs.core.nth.call(null,vec__3318,(2),null);
var G__3321 = op;
switch (G__3321) {
case "insert":
return ({"range": range.call(null,i,i), "text": arg});

break;
case "remove":
return ({"range": range.call(null,i,(i + arg)), "text": null});

break;
default:
throw (new Error(["No matching clause: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(G__3321)].join('')));

}
});})(model,range))
,pinfo.changes);
model.pushEditOperations(null,cljs.core.into_array.call(null,chgs),null);

return editor.setPosition(model.getPositionAt(pinfo.newIndex));
} else {
return null;
}
});
cognitect.monaco.paredit.wrap_paredit_command = (function cognitect$monaco$paredit$wrap_paredit_command(cmd){
return (function (editor){
var model = editor.getModel();
var src = model.getValue();
var pos = editor.getPosition();
return cmd.call(null,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"editor","editor",-989377770),editor,new cljs.core.Keyword(null,"src","src",-1651076051),src,new cljs.core.Keyword(null,"ast","ast",-860334068),cognitect.monaco.paredit.paredit_ast.call(null,src),new cljs.core.Keyword(null,"selection","selection",975998651),cognitect.monaco.paredit.get_selection.call(null,editor)], null));
});
});
cognitect.monaco.paredit.nav_thunk = (function cognitect$monaco$paredit$nav_thunk(paredit_cmd){
return cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3323){
var map__3324 = p__3323;
var map__3324__$1 = (((((!((map__3324 == null))))?(((((map__3324.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3324.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3324):map__3324);
var editor = cljs.core.get.call(null,map__3324__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3324__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3324__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3324__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
var model = editor.getModel();
var nidx = paredit_cmd.call(null,ast,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection));
return editor.setPosition(model.getPositionAt(nidx));
}));
});
cognitect.monaco.paredit.actions = new cljs.core.PersistentVector(null, 12, 5, cljs.core.PersistentVector.EMPTY_NODE, [({"id": "paredit-slurp-forward", "label": "Slurp Forward", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Shift) | monaco.KeyCode.KEY_0)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3326){
var map__3327 = p__3326;
var map__3327__$1 = (((((!((map__3327 == null))))?(((((map__3327.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3327.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3327):map__3327);
var editor = cljs.core.get.call(null,map__3327__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3327__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3327__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3327__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.slurpSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": false})));
}))}),({"id": "paredit-slurp-backward", "label": "Slurp Forward", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Shift) | monaco.KeyCode.KEY_9)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3329){
var map__3330 = p__3329;
var map__3330__$1 = (((((!((map__3330 == null))))?(((((map__3330.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3330.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3330):map__3330);
var editor = cljs.core.get.call(null,map__3330__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3330__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3330__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3330__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.slurpSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": true})));
}))}),({"id": "paredit-barf-sexp", "label": "Barf S-Expression", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Shift) | monaco.KeyCode.US_CLOSE_SQUARE_BRACKET)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3332){
var map__3333 = p__3332;
var map__3333__$1 = (((((!((map__3333 == null))))?(((((map__3333.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3333.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3333):map__3333);
var editor = cljs.core.get.call(null,map__3333__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3333__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3333__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3333__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.barfSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": false})));
}))}),({"id": "paredit-barf-sexp-backward", "label": "Barf S-Expression Backwards", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Shift) | monaco.KeyCode.US_OPEN_SQUARE_BRACKET)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3335){
var map__3336 = p__3335;
var map__3336__$1 = (((((!((map__3336 == null))))?(((((map__3336.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3336.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3336):map__3336);
var editor = cljs.core.get.call(null,map__3336__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3336__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3336__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3336__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.barfSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": true})));
}))}),({"id": "paredit-kill-sexp", "label": "Kill S-Expression", "keybindings": [(monaco.KeyMod.WinCtrl | monaco.KeyCode.KEY_D)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3338){
var map__3339 = p__3338;
var map__3339__$1 = (((((!((map__3339 == null))))?(((((map__3339.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3339.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3339):map__3339);
var editor = cljs.core.get.call(null,map__3339__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3339__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3339__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3339__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.killSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": false})));
}))}),({"id": "paredit-kill-sexp-backward", "label": "Kill S-Expression Backwards", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Shift) | monaco.KeyCode.KEY_D)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3341){
var map__3342 = p__3341;
var map__3342__$1 = (((((!((map__3342 == null))))?(((((map__3342.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3342.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3342):map__3342);
var editor = cljs.core.get.call(null,map__3342__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3342__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3342__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3342__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.killSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({"backward": true})));
}))}),({"id": "paredit-split-sexp", "label": "Split S-Expression", "keybindings": [((monaco.KeyMod.Alt | monaco.KeyMod.Shift) | monaco.KeyCode.KEY_S)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3344){
var map__3345 = p__3344;
var map__3345__$1 = (((((!((map__3345 == null))))?(((((map__3345.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3345.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3345):map__3345);
var editor = cljs.core.get.call(null,map__3345__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3345__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3345__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3345__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.splitSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({})));
}))}),({"id": "paredit-splice-sexp", "label": "Splice S-Expression", "keybindings": [(monaco.KeyMod.Alt | monaco.KeyCode.KEY_S)], "run": cognitect.monaco.paredit.wrap_paredit_command.call(null,(function (p__3347){
var map__3348 = p__3347;
var map__3348__$1 = (((((!((map__3348 == null))))?(((((map__3348.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__3348.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__3348):map__3348);
var editor = cljs.core.get.call(null,map__3348__$1,new cljs.core.Keyword(null,"editor","editor",-989377770));
var ast = cljs.core.get.call(null,map__3348__$1,new cljs.core.Keyword(null,"ast","ast",-860334068));
var src = cljs.core.get.call(null,map__3348__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var selection = cljs.core.get.call(null,map__3348__$1,new cljs.core.Keyword(null,"selection","selection",975998651));
return cognitect.monaco.paredit.apply_edits.call(null,editor,paredit.editor.spliceSexp(ast,src,new cljs.core.Keyword(null,"cur","cur",1153190599).cljs$core$IFn$_invoke$arity$1(selection),({})));
}))}),({"id": "paredit-forward", "label": "Forward S-Expression", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Alt) | monaco.KeyCode.KEY_F)], "run": cognitect.monaco.paredit.nav_thunk.call(null,paredit.navigator.forwardSexp)}),({"id": "paredit-backward", "label": "Backward S-Expression", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Alt) | monaco.KeyCode.KEY_B)], "run": cognitect.monaco.paredit.nav_thunk.call(null,paredit.navigator.backwardSexp)}),({"id": "paredit-forward-down", "label": "Forward Down S-Expression", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Alt) | monaco.KeyCode.KEY_D)], "run": cognitect.monaco.paredit.nav_thunk.call(null,paredit.navigator.forwardDownSexp)}),({"id": "paredit-backward-up", "label": "Backward Up S-Expression", "keybindings": [((monaco.KeyMod.WinCtrl | monaco.KeyMod.Alt) | monaco.KeyCode.KEY_U)], "run": cognitect.monaco.paredit.nav_thunk.call(null,paredit.navigator.backwardUpSexp)})], null);
cognitect.monaco.paredit.register_actions = (function cognitect$monaco$paredit$register_actions(editor){
var seq__3350 = cljs.core.seq.call(null,cognitect.monaco.paredit.actions);
var chunk__3351 = null;
var count__3352 = (0);
var i__3353 = (0);
while(true){
if((i__3353 < count__3352)){
var a = cljs.core._nth.call(null,chunk__3351,i__3353);
editor.addAction(a);


var G__3354 = seq__3350;
var G__3355 = chunk__3351;
var G__3356 = count__3352;
var G__3357 = (i__3353 + (1));
seq__3350 = G__3354;
chunk__3351 = G__3355;
count__3352 = G__3356;
i__3353 = G__3357;
continue;
} else {
var temp__5720__auto__ = cljs.core.seq.call(null,seq__3350);
if(temp__5720__auto__){
var seq__3350__$1 = temp__5720__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__3350__$1)){
var c__4461__auto__ = cljs.core.chunk_first.call(null,seq__3350__$1);
var G__3358 = cljs.core.chunk_rest.call(null,seq__3350__$1);
var G__3359 = c__4461__auto__;
var G__3360 = cljs.core.count.call(null,c__4461__auto__);
var G__3361 = (0);
seq__3350 = G__3358;
chunk__3351 = G__3359;
count__3352 = G__3360;
i__3353 = G__3361;
continue;
} else {
var a = cljs.core.first.call(null,seq__3350__$1);
editor.addAction(a);


var G__3362 = cljs.core.next.call(null,seq__3350__$1);
var G__3363 = null;
var G__3364 = (0);
var G__3365 = (0);
seq__3350 = G__3362;
chunk__3351 = G__3363;
count__3352 = G__3364;
i__3353 = G__3365;
continue;
}
} else {
return null;
}
}
break;
}
});
goog.exportSymbol('cognitect.monaco.paredit.register_actions', cognitect.monaco.paredit.register_actions);
