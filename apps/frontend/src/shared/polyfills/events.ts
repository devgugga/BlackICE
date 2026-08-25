type Listener = (...args: any[]) => void;

export class EventEmitter {
  private _events: Record<string, Listener[]> = {};

  on(event: string, listener: Listener): this {
    (this._events[event] ??= []).push(listener);
    return this;
  }

  addListener(event: string, listener: Listener): this {
    return this.on(event, listener);
  }

  once(event: string, listener: Listener): this {
    const wrapped: Listener = (...args: any[]) => {
      this.off(event, wrapped);
      listener(...args);
    };
    return this.on(event, wrapped);
  }

  off(event: string, listener: Listener): this {
    const list = this._events[event];
    if (list) {
      this._events[event] = list.filter((l) => l !== listener);
    }
    return this;
  }

  removeListener(event: string, listener: Listener): this {
    return this.off(event, listener);
  }

  removeAllListeners(event?: string): this {
    if (event) {
      delete this._events[event];
    } else {
      this._events = {};
    }
    return this;
  }

  emit(event: string, ...args: any[]): boolean {
    const list = this._events[event];
    if (!list || list.length === 0) return false;
    for (const listener of [...list]) {
      listener(...args);
    }
    return true;
  }

  listenerCount(event: string): number {
    return this._events[event]?.length ?? 0;
  }
}

export default {
  EventEmitter,
};
